package com.example.barscanner.ui.view

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class BarcodeViewModel : ViewModel() 
{

    private val _text = MutableLiveData<String>().apply { value = "This is the barcode Fragment" }
    val text: LiveData<String> = _text
}