package com.example.barcode

import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.barscanner.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() 
{

    private lateinit var binding: ActivityMainBinding

    private val requestCameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> }

    override fun onCreate(savedInstanceState: Bundle?) 
    {
      super.onCreate(savedInstanceState)

      supportActionBar?.hide()

      binding = ActivityMainBinding.inflate(layoutInflater)
      setContentView(binding.root)
      requestCameraPermission.launch(android.Manifest.permission.CAMERA)
    }
}
