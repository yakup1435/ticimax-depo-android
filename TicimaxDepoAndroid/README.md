# Ticimax Depo Android

Telefon kamerasıyla barkod okuyan ve Ticimax Ürün Servisi'ne doğrudan bağlanan Android uygulama projesidir.

## Canlı özellikler
- Kamera ile barkod okuma
- Ayarlar ekranından mağaza adresi + Üye Kodu
- Barkoda göre `SelectVaryasyon` ile canlı ürün/varyasyon sorgulama
- Canlı stok güncelleme: `StokAdediGuncelle`
- Canlı satış fiyatı güncelleme: `VaryasyonGuncelle` + `SatisFiyatiGuncelle`
- Stok +/- ve manuel stok/fiyat girişi
- Servis hatasını uygulama ekranında gösterme

## Önemli
Bu sürümde Kaydet butonu GERÇEK Ticimax verisini değiştirir. Önce tek bir test ürünüyle doğrulamak önerilir.

Ticimax resmi dokümanına göre barkod `SelectVaryasyon` filtresinde kullanılabilir; stok güncellemede Varyasyon ID ve StokAdedi yeterlidir. Fiyat `VaryasyonGuncelle` içinde `SatisFiyatiGuncelle=true` ile değiştirilir.

## Derleme
Android Studio ile klasörü açın, Gradle Sync yapın, ardından Build > Build APK(s).
