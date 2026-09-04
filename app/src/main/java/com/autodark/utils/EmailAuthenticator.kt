package com.autodark.utils

import javax.mail.Authenticator
import javax.mail.PasswordAuthentication

class EmailAuthenticator(private val userName: String, private val password: String) :
    Authenticator() {

    override fun getPasswordAuthentication(): PasswordAuthentication {
        return PasswordAuthentication(userName, password)
    }
}