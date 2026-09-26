package com.yakup.ticimaxdepo;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.*;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.common.InputImage;
import java.util.concurrent.*;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private static final int CAM=99;
    PreviewView preview; EditText barcode, stock, price; TextView scanStatus, product, info;
    ExecutorService cameraExecutor; boolean busy=false;
    TicimaxClient.Variation current=null;
    double loadedStock=0, loadedPrice=0;

    @Override protected void onCreate(Bundle b){ super.onCreate(b); setContentView(R.layout.activity_main);
        preview=findViewById(R.id.preview); barcode=findViewById(R.id.barcode); stock=findViewById(R.id.stock); price=findViewById(R.id.price);
        scanStatus=findViewById(R.id.scanStatus); product=findViewById(R.id.product); info=findViewById(R.id.info); cameraExecutor=Executors.newSingleThreadExecutor();
        findViewById(R.id.settings).setOnClickListener(v->startActivity(new Intent(this,SettingsActivity.class)));
        findViewById(R.id.find).setOnClickListener(v->lookup(barcode.getText().toString().trim()));
        findViewById(R.id.minus).setOnClickListener(v->change(-1)); findViewById(R.id.plus).setOnClickListener(v->change(1));
        findViewById(R.id.saveProduct).setOnClickListener(v->saveLive());
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)== PackageManager.PERMISSION_GRANTED) startCamera(); else ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.CAMERA},CAM);
    }

    void change(int d){ try{ double n=Double.parseDouble(stock.getText().toString().replace(',','.')); stock.setText(format(Math.max(0,n+d))); }catch(Exception ignored){} }

    void lookup(String code){
        if(code.isEmpty()) return;
        if(AppPrefs.domain(this).isEmpty() || AppPrefs.code(this).isEmpty()){ info.setText("Önce Ayarlar'dan Ticimax site adresi ve Üye Kodu girin."); return; }
        info.setText("Ticimax'tan ürün aranıyor..."); product.setText("Barkod: "+code); current=null;
        Executors.newSingleThreadExecutor().execute(()->{
            try{
                TicimaxClient.Variation v=TicimaxClient.selectVariationByBarcode(AppPrefs.domain(this),AppPrefs.code(this),code);
                runOnUiThread(()->{
                    current=v; loadedStock=v.stock; loadedPrice=v.price;
                    stock.setText(format(v.stock)); price.setText(format(v.price));
                    product.setText("Barkod: "+v.barcode+"  |  Varyasyon ID: "+v.id);
                    info.setText("Ürün bulundu"+(v.stockCode.isEmpty()?"":" • Stok kodu: "+v.stockCode)+"\nMevcut stok: "+format(v.stock)+" • Fiyat: "+format(v.price)+" ₺");
                });
            }catch(Exception e){ runOnUiThread(()->info.setText("Ürün sorgu hatası: "+e.getMessage())); }
        });
    }

    void saveLive(){
        if(current==null){ Toast.makeText(this,"Önce barkodu bulup ürünü yükleyin.",Toast.LENGTH_LONG).show(); return; }
        final double newStock, newPrice;
        try{
            newStock=Double.parseDouble(stock.getText().toString().replace(',','.'));
            newPrice=Double.parseDouble(price.getText().toString().replace(',','.'));
        }catch(Exception e){ Toast.makeText(this,"Stok veya fiyat geçersiz.",Toast.LENGTH_LONG).show(); return; }
        boolean stockChanged=Math.abs(newStock-loadedStock)>0.000001;
        boolean priceChanged=Math.abs(newPrice-loadedPrice)>0.000001;
        if(!stockChanged && !priceChanged){ Toast.makeText(this,"Değişiklik yok.",Toast.LENGTH_SHORT).show(); return; }
        info.setText("CANLI Ticimax güncellemesi gönderiliyor...");
        findViewById(R.id.saveProduct).setEnabled(false);
        Executors.newSingleThreadExecutor().execute(()->{
            try{
                TicimaxClient.UpdateResult r=TicimaxClient.updateStockAndPrice(AppPrefs.domain(this),AppPrefs.code(this),current,newStock,newPrice,stockChanged,priceChanged);
                runOnUiThread(()->{
                    findViewById(R.id.saveProduct).setEnabled(true);
                    if(r.stockOk && r.priceOk){
                        loadedStock=newStock; loadedPrice=newPrice; current.stock=newStock; current.price=newPrice;
                        info.setText("✅ CANLI güncelleme tamamlandı. "+r.message);
                        Toast.makeText(this,"Ticimax güncellendi",Toast.LENGTH_LONG).show();
                    } else info.setText("⚠️ Güncelleme yanıtı: "+r.message);
                });
            }catch(Exception e){ runOnUiThread(()->{ findViewById(R.id.saveProduct).setEnabled(true); info.setText("❌ Canlı güncelleme hatası: "+e.getMessage()); }); }
        });
    }

    String format(double d){
        if(Math.abs(d-Math.rint(d))<0.000001) return String.valueOf((long)Math.rint(d));
        return String.format(Locale.US,"%.2f",d);
    }

    void startCamera(){
        ListenableFuture<ProcessCameraProvider> f=ProcessCameraProvider.getInstance(this);
        f.addListener(()->{ try{
            ProcessCameraProvider p=f.get(); Preview pr=new Preview.Builder().build(); pr.setSurfaceProvider(preview.getSurfaceProvider());
            ImageAnalysis a=new ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build();
            BarcodeScanner scanner= BarcodeScanning.getClient();
            a.setAnalyzer(cameraExecutor, imageProxy->{
                if(busy){ imageProxy.close(); return; }
                android.media.Image media=imageProxy.getImage(); if(media==null){ imageProxy.close(); return; }
                InputImage img=InputImage.fromMediaImage(media,imageProxy.getImageInfo().getRotationDegrees()); busy=true;
                scanner.process(img).addOnSuccessListener(list->{ if(!list.isEmpty() && list.get(0).getRawValue()!=null){ String v=list.get(0).getRawValue(); runOnUiThread(()->{ if(!v.equals(barcode.getText().toString())){ barcode.setText(v); scanStatus.setText("Barkod okundu: "+v); lookup(v); } }); }}).addOnCompleteListener(t->{ busy=false; imageProxy.close(); });
            });
            p.unbindAll(); p.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA,pr,a);
        }catch(Exception e){ scanStatus.setText("Kamera hatası: "+e.getMessage()); } }, ContextCompat.getMainExecutor(this));
    }
    @Override public void onRequestPermissionsResult(int r,@NonNull String[] p,@NonNull int[] g){ super.onRequestPermissionsResult(r,p,g); if(r==CAM && g.length>0 && g[0]==PackageManager.PERMISSION_GRANTED) startCamera(); }
    @Override protected void onDestroy(){ super.onDestroy(); cameraExecutor.shutdown(); }
}
