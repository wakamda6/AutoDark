package com.pengxh.autodingding.utils

import javax.mail.Authenticator
import javax.mail.PasswordAuthentication

/**
 * @author: Pengxh
 * @email: ***REMOVED***
 * @date: 2020/1/16 15:42
 */
class EmailAuthenticator(private val userName: String, private val password: String) :
    Authenticator() {

    override fun getPasswordAuthentication(): PasswordAuthentication {
        return PasswordAuthentication(userName, password)
    }
}