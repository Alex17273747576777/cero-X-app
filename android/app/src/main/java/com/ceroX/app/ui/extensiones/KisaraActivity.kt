package com.ceroX.app.ui.extensiones

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.ceroX.app.modules.kisara.KisaraUiFragment

class KisaraActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Biblioteca Kisara"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(android.R.id.content, KisaraUiFragment()).commit()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
