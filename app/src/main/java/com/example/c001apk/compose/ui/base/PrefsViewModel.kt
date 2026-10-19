package com.example.c001apk.compose.ui.base

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.c001apk.compose.logic.repository.UserPreferencesRepository
import com.example.c001apk.compose.util.IosDeviceUtils
import kotlinx.coroutines.launch

/**
 * Created by bggRGjQaUbCoE on 2024/6/15
 */
open class PrefsViewModel(
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    fun regenerateParams() {
        viewModelScope.launch { IosDeviceUtils.regenerate() }
    }
}
