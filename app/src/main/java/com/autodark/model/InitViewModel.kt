package com.autodark.model

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.autodark.utils.CertificateManager
import kotlinx.coroutines.launch


class InitViewModel(application: Application) : AndroidViewModel(application) {

    private val _initState = MutableLiveData<InitState>()
    val initState: LiveData<InitState> get() = _initState

    fun initCertificateCheck(ID: String) {
        viewModelScope.launch {
            _initState.postValue(InitState.Loading) // 正在初始化

            val context = getApplication<Application>().applicationContext

            when (val result = CertificateManager.getAndCheckCA(context, ID)) {
                "CASuccess" -> _initState.postValue(InitState.Success)
                "CAGetFailed" -> _initState.postValue(InitState.Failed("证书获取或解密失败"))
                "SSLError" -> _initState.postValue(InitState.Failed("SSL 初始化失败"))
                else -> _initState.postValue(InitState.Failed("未知错误：$result"))
            }
        }
    }
}

sealed class InitState {
    object Loading : InitState()
    object Success : InitState()
    data class Failed(val reason: String) : InitState()
}

