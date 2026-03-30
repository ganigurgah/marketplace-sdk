## 📝 PR Özeti
<!-- Yaptığınız değişiklikleri ve nedenini kısaca açıklayın -->
Bu PR şu sorunu çözüyor / şu özelliği ekliyor: [Konu]

## 🛠️ Değişiklik Tipi
<!-- Uygun olanı 'x' ile işaretleyin: [x] -->
- [ ] 🐛 Bug fix (Hata düzeltme)
- [ ] ✨ Yeni Özellik (Yeni bir pazaryeri adapter'ı veya yetenek)
- [ ] ♻️ Refactoring (Kod iyileştirmesi)
- [ ] 📚 Dokümantasyon

## ✅ Kabul Kriterleri (Zorunlu)
<!-- PR'nızın kabul edilmesi için aşağıdaki adımları tamamladığınızdan emin olun -->
- [ ] Adapter kodları `io.marketplace.sdk.core.spi.MarketplaceAdapter` implementasyonuna uygun.
- [ ] Tüm base URL, credentials veya mapping işlemleri **YAML config tabanlı** yapılmış, hard-coded string kullanılmamıştır.
- [ ] Eklenen API/Pazaryeri için **%100 kapsama sahip WireMock Testleri** yazılıp `sdk-test-support` içine eklenmiştir.
- [ ] `mvn clean test` lokal ortamımda hata vermeden geçmiştir.
- [ ] (Varsa) Yeni yetenek `SKILL.md` belgesine dokümante edilmiştir.

## 📷 Ekran Görüntüleri / Loglar (Varsa)
<!-- Test sonuçlarının konsol çıktısını veya HTTP payload/response body örneğini buraya ekleyebilirsiniz -->
```json
// Örnek Request Payload
```

## 🔗 İlgili Kaynak / Issue Bağlantısı
Fixes # (Issue Numarası)
