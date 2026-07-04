package com.example

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

data class UserProfile(
    val id: String,
    val name: String,
    val email: String,
    val photoUrl: String,
    val provider: String
)

object AuthManager {
    var currentUser by mutableStateOf<UserProfile?>(null)

    fun init(context: Context) {
        // Local-only mode, no initialization needed
    }
}
