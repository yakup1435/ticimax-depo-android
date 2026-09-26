package com.yakup.ticimaxdepo;

import android.os.Bundle;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {
    EditText domain, memberCode; TextView status;
    @Override protected void onCreate(Bundle b){ super.onCreate(b); setContentView(R.layout.activity_settings);
        domain=findViewById(R.id.domain); memberCode=findViewById(R.id.memberCode); status=findViewById(R.id.status);
        domain.setText(AppPrefs.domain(this)); memberCode.setText(AppPrefs.code(this));
        findViewById(R.id.save).setOnClickListener(v->{ AppPrefs.save(this,domain.getText().toString().trim(),memberCode.getText().toString().trim()); Toast.makeText(this,"Kaydedildi",Toast.LENGTH_SHORT).show(); finish(); });
        findViewById(R.id.test).setOnClickListener(v->{ status.setText("Bağlantı kontrol ediliyor..."); new Thread(()->{ try{ boolean ok=TicimaxClient.testWsdl(domain.getText().toString()); runOnUiThread(()->status.setText(ok?"✓ Ticimax Ürün Servisine erişildi.":"Servise erişilemedi.")); } catch(Exception e){ runOnUiThread(()->status.setText("Hata: "+e.getMessage())); } }).start(); });
    }
}
