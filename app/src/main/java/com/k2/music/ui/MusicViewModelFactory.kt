package com.k2.music.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import kotlin.reflect.KClass

/** Small explicit factory that gives every feature ViewModel a SavedStateHandle. */
class MusicViewModelFactory<VM : ViewModel>(
    private val expectedClass: KClass<VM>,
    private val creator: (SavedStateHandle) -> VM,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        require(modelClass.isAssignableFrom(expectedClass.java)) {
            "Unsupported ViewModel: ${modelClass.name}"
        }
        @Suppress("UNCHECKED_CAST")
        return creator(extras.createSavedStateHandle()) as T
    }
}
