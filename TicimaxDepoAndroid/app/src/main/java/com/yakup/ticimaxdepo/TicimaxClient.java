package com.yakup.ticimaxdepo;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.regex.*;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;
import org.xml.sax.InputSource;

public class TicimaxClient {
    private static final String SOAP_NS = "http://schemas.xmlsoap.org/soap/envelope/";
    private static final String TEMPURI = "http://tempuri.org/";
    // Ticimax WCF servislerinde yaygın DataContract namespace'i. WSDL farklıysa hata metni ekranda gösterilir.
    private static final String DATA_NS = "http://schemas.datacontract.org/2004/07/UrunServis";

    public static class Variation {
        public int id;
        public String barcode = "";
        public String stockCode = "";
        public double stock;
        public double price;
    }

    public static class UpdateResult {
        public boolean stockOk;
        public boolean priceOk;
        public String message = "";
    }

    public static String serviceUrl(String domain){
        domain = domain == null ? "" : domain.trim();
        if(!domain.startsWith("http://") && !domain.startsWith("https://")) domain = "https://" + domain;
        while(domain.endsWith("/")) domain = domain.substring(0,domain.length()-1);
        return domain + "/Servis/UrunServis.svc";
    }

    public static boolean testWsdl(String domain) throws Exception {
        URL u = new URL(serviceUrl(domain) + "?wsdl");
        HttpURLConnection c = (HttpURLConnection)u.openConnection();
        c.setConnectTimeout(10000); c.setReadTimeout(10000); c.setRequestMethod("GET");
        int code = c.getResponseCode();
        if(code < 200 || code >= 400) return false;
        String s = read(c.getInputStream()).toLowerCase();
        return s.contains("definitions") || s.contains("wsdl") || s.contains("selectvaryasyon");
    }

    public static Variation selectVariationByBarcode(String domain, String memberCode, String barcode) throws Exception {
        // Güncel Ticimax WSDL'lerinde parametre adları f ve s olarak yayınlanabiliyor.
        String body =
            "<tem:SelectVaryasyon>" +
            "<tem:UyeKodu>"+xml(memberCode)+"</tem:UyeKodu>" +
            "<tem:f>" +
              "<dat:Aktif>-1</dat:Aktif>" +
              "<dat:Barkod>"+xml(barcode)+"</dat:Barkod>" +
              "<dat:StokKodu></dat:StokKodu>" +
              "<dat:UrunID>-1</dat:UrunID>" +
              "<dat:UrunKartiID>-1</dat:UrunKartiID>" +
            "</tem:f>" +
            "<tem:s>" +
              "<dat:BaslangicIndex>0</dat:BaslangicIndex>" +
              "<dat:KayitSayisi>10</dat:KayitSayisi>" +
              "<dat:SiralamaDegeri>ID</dat:SiralamaDegeri>" +
              "<dat:SiralamaYonu>Asc</dat:SiralamaYonu>" +
            "</tem:s>" +
            "</tem:SelectVaryasyon>";
        String response = soap(domain, "SelectVaryasyon", body);
        checkFault(response);
        Variation v = parseVariation(response, barcode);
        if(v == null) throw new Exception("Bu barkoda ait varyasyon bulunamadı: " + barcode);
        return v;
    }

    public static UpdateResult updateStockAndPrice(String domain, String memberCode, Variation v,
                                                   double newStock, double newPrice,
                                                   boolean changeStock, boolean changePrice) throws Exception {
        UpdateResult r = new UpdateResult();
        StringBuilder msg = new StringBuilder();

        if(changeStock){
            String body =
                "<tem:StokAdediGuncelle>" +
                "<tem:UyeKodu>"+xml(memberCode)+"</tem:UyeKodu>" +
                "<tem:Urunler>" +
                  "<dat:Varyasyon><dat:ID>"+v.id+"</dat:ID><dat:StokAdedi>"+num(newStock)+"</dat:StokAdedi></dat:Varyasyon>" +
                "</tem:Urunler>" +
                "</tem:StokAdediGuncelle>";
            String response = soap(domain, "StokAdediGuncelle", body);
            checkFault(response);
            r.stockOk = isSuccessResponse(response, "StokAdediGuncelleResult");
            msg.append(r.stockOk ? "Stok güncellendi. " : "Stok servisi başarısız döndü. ");
        } else r.stockOk = true;

        if(changePrice){
            String body =
                "<tem:VaryasyonGuncelle>" +
                "<tem:UyeKodu>"+xml(memberCode)+"</tem:UyeKodu>" +
                "<tem:Varyasyon>" +
                  "<dat:ID>"+v.id+"</dat:ID>" +
                  "<dat:SatisFiyati>"+num(newPrice)+"</dat:SatisFiyati>" +
                "</tem:Varyasyon>" +
                "<tem:VaryasyonAyar>" +
                  "<dat:SatisFiyatiGuncelle>true</dat:SatisFiyatiGuncelle>" +
                  "<dat:StokAdediGuncelle>false</dat:StokAdediGuncelle>" +
                "</tem:VaryasyonAyar>" +
                "</tem:VaryasyonGuncelle>";
            String response = soap(domain, "VaryasyonGuncelle", body);
            checkFault(response);
            r.priceOk = isSuccessResponse(response, "VaryasyonGuncelleResult");
            msg.append(r.priceOk ? "Fiyat güncellendi." : "Fiyat servisi başarısız döndü.");
        } else r.priceOk = true;

        r.message = msg.toString().trim();
        return r;
    }

    private static String soap(String domain, String action, String body) throws Exception {
        String envelope = "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
            "<soap:Envelope xmlns:soap=\""+SOAP_NS+"\" xmlns:tem=\""+TEMPURI+"\" xmlns:dat=\""+DATA_NS+"\">" +
            "<soap:Body>"+body+"</soap:Body></soap:Envelope>";
        byte[] bytes = envelope.getBytes(StandardCharsets.UTF_8);
        HttpURLConnection c = (HttpURLConnection)new URL(serviceUrl(domain)).openConnection();
        c.setConnectTimeout(15000); c.setReadTimeout(30000);
        c.setRequestMethod("POST"); c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "text/xml; charset=utf-8");
        c.setRequestProperty("SOAPAction", "\""+TEMPURI+"IUrunServis/"+action+"\"");
        c.setRequestProperty("Accept", "text/xml");
        c.setFixedLengthStreamingMode(bytes.length);
        try(OutputStream os=c.getOutputStream()){ os.write(bytes); }
        int code=c.getResponseCode();
        InputStream in = code>=400 ? c.getErrorStream() : c.getInputStream();
        String response = in == null ? "" : read(in);
        if(code<200 || code>=300) throw new Exception("Ticimax HTTP " + code + ": " + stripXml(response));
        return response;
    }

    private static Variation parseVariation(String xml, String wantedBarcode) throws Exception {
        DocumentBuilderFactory f=DocumentBuilderFactory.newInstance(); f.setNamespaceAware(true);
        Document d=f.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        NodeList all=d.getElementsByTagNameNS("*","Varyasyon");
        Variation first=null;
        for(int i=0;i<all.getLength();i++){
            Element e=(Element)all.item(i); Variation v=new Variation();
            v.id=intVal(child(e,"ID")); v.barcode=child(e,"Barkod"); v.stockCode=child(e,"StokKodu");
            v.stock=doubleVal(child(e,"StokAdedi")); v.price=doubleVal(child(e,"SatisFiyati"));
            if(v.id<=0) continue;
            if(first==null) first=v;
            if(wantedBarcode.equals(v.barcode)) return v;
        }
        return first;
    }

    private static String child(Element parent,String local){
        NodeList nl=parent.getElementsByTagNameNS("*",local);
        return nl.getLength()==0 ? "" : nl.item(0).getTextContent().trim();
    }
    private static int intVal(String s){ try{return Integer.parseInt(s);}catch(Exception e){return 0;} }
    private static double doubleVal(String s){ try{return Double.parseDouble(s.replace(',', '.'));}catch(Exception e){return 0;} }
    private static String num(double d){ return java.math.BigDecimal.valueOf(d).stripTrailingZeros().toPlainString(); }

    private static boolean isSuccessResponse(String response, String resultName){
        try{
            DocumentBuilderFactory f=DocumentBuilderFactory.newInstance(); f.setNamespaceAware(true);
            Document d=f.newDocumentBuilder().parse(new InputSource(new StringReader(response)));
            NodeList n=d.getElementsByTagNameNS("*",resultName);
            if(n.getLength()==0) return true; // bazı void metodlar boş cevap döndürebilir
            String s=n.item(0).getTextContent().trim();
            return s.isEmpty() || s.equals("1") || s.equalsIgnoreCase("true");
        }catch(Exception e){ return false; }
    }

    private static void checkFault(String xml) throws Exception {
        if(xml==null) return;
        String low=xml.toLowerCase();
        if(low.contains("<fault") || low.contains(":fault")) throw new Exception("Ticimax SOAP hatası: " + stripXml(xml));
    }
    private static String xml(String s){ return s==null?"":s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&apos;"); }
    private static String read(InputStream in) throws Exception { try(BufferedReader br=new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))){ StringBuilder b=new StringBuilder(); String l; while((l=br.readLine())!=null)b.append(l); return b.toString(); } }
    private static String stripXml(String s){
        if(s==null) return "";
        String t=s.replaceAll("<[^>]+>"," ").replaceAll("\\s+"," ").trim();
        return t.length()>600?t.substring(0,600):t;
    }
}
