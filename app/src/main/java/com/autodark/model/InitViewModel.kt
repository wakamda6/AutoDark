package com.autodark.model

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.autodark.utils.CertCheckResult
import com.autodark.utils.CertificateManager
import kotlinx.coroutines.launch


class InitViewModel(application: Application) : AndroidViewModel(application) {

    private val _initState = MutableLiveData<InitState>()
    val initState: LiveData<InitState> get() = _initState

    fun initCertificateCheck(ID: String) {
        viewModelScope.launch {
            val context = getApplication<Application>().applicationContext

            when (val result = CertificateManager.getAndCheckCA(context, ID)) {
                is CertCheckResult -> {
                    when (result.status) {
                        CertCheckResult.Status.CASuccess -> {
                            _initState.postValue(InitState.Success(result.message)) // 传递剩余时长
                        }
                        CertCheckResult.Status.CAGetFailed -> {
                            _initState.postValue(InitState.Failed("证书获取失败\n请联系开发者 ID:$ID"))
                        }
                        CertCheckResult.Status.CADecodeFailed -> {
                            _initState.postValue(InitState.Failed("证书解密失败\n请联系开发者 ID:$ID"))
                        }
                        CertCheckResult.Status.CAisRevoked -> {
                            _initState.postValue(InitState.Failed("证书已过期\n请联系开发者 ID:$ID"))
                        }
                        CertCheckResult.Status.CheckCertRevokedError -> {
                            _initState.postValue(InitState.Failed("证书验证错误\n请联系开发者 ID:$ID"))
                        }
                        CertCheckResult.Status.SSLError -> {
                            _initState.postValue(InitState.Failed("SSL 初始化失败\n请联系开发者 ID:$ID"))
                        }
                    }
                }
            }

        }
    }
}

sealed class InitState {
    data class Success(val remaining: String = "") : InitState()
    data class Failed(val reason: String) : InitState()
}

