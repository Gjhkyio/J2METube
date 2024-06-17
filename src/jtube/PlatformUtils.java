/*
Copyright (c) 2022 Arman Jussupgaliyev

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
*/
package jtube;

public class PlatformUtils {

	public static final long _2MB = 2048 * 1024;
	public static final long _1MB = 1024 * 1024;
	public static final long ASHA_MEM = 2621424;
	
	public static final String platform;
	public static final long startMemory = Runtime.getRuntime().totalMemory();
	public static final String os = System.getProperty("os.name");
	public static final String vendor = System.getProperty("java.vendor");
	public static final String version = System.getProperty("java.version");
	
	public static boolean isKemulator;
	public static boolean isS60;
	public static boolean isAsha;
	public static boolean isBada;
	public static boolean isJ2MELoader;
	public static boolean isS40;
	public static boolean isJ9;
	
	public static int width;
	public static int height;
	
	static {
		String p = System.getProperty("microedition.platform");
		if(p == null) p = "";
		platform = p;
		isKemulator = checkClass("emulator.custom.CustomMethod");
		isJ2MELoader = checkClass("javax.microedition.shell.MicroActivity");
		isS60 = isSymbian();
		// s40 check
		isS40 = checkClass("javax.microedition.midlet.MIDletProxy") || checkClass("com.nokia.mid.impl.isa.jam.Jam");
		// asha check
		String s40v = getS40_version();
		isAsha = isNokia() && s40v != null && (s40v.startsWith("7") || s40v.startsWith("8") || s40v.startsWith("9"));
		// bada check
		String s2;
		if(platform.startsWith("SAMSUNG-GT-")) {
			s2 = platform.substring("SAMSUNG-GT-".length());
			if(s2.startsWith("S")) s2 = s2.substring(1);
			isBada = s2.startsWith("538")
							|| s2.startsWith("85") || s2.startsWith("72") || s2.startsWith("525")
							|| s2.startsWith("533") || s2.startsWith("57")|| s2.startsWith("86");
		}
		isJ9 = platform.indexOf("sw_platform=S60") != -1;
	}
	
	private static boolean isJ9S60Version(String v) {
		return isJ9 && platform.indexOf("sw_platform_version=" + v) != -1;
	}
	
	public static boolean isSymbianJRT2() {
		return isJ9 && platform.indexOf("java_build_version=2.") != -1;
	}
	
	public static boolean isS60v5() {
		return isJ9 && isJ9S60Version("5");
	}
	
	public static boolean isSymbian3Based() {
		return isS60v5() && !isJ9S60Version("5.0");
	}
	
	public static boolean isSymbian94() {
		return isJ9 && isJ9S60Version("5.0");
	}

	public static boolean isSymbianAnna() {
		return isJ9 && isJ9S60Version("5.2");
	}

	public static boolean isBelle() {
		int i = platform.indexOf("sw_platform_version=5.") + "sw_platform_version=5.".length();
		return isJ9 && isJ9S60Version("5") && Integer.parseInt(platform.substring(i, i+1)) >= 3;
	}
	
	public static boolean isSymbian93() {
		return isJ9S60Version("3.2");
	}

	public static boolean isS60v3() {
		return isS60 && getS60().startsWith("3");
	}
	
	// returns true for s60 verions lower than 5.0
	public static boolean isS60v3orLower() {
		return isS60 && !getS60().startsWith("5");
	}

	// Symbian check
	public static boolean isSymbian() {
		return platform.indexOf("platform=S60") != -1 ||
				System.getProperty("com.symbian.midp.serversocket.support") != null ||
				System.getProperty("com.symbian.default.to.suite.icon") != null ||
				checkClass("com.symbian.midp.io.protocol.http.Protocol") ||
				checkClass("com.symbian.lcdjava.io.File");
	}
	
	// returns the estimated s60 version
	public static String getS60() {
		if(!isS60) {
			return null;
		}
		if(isJ9) {
			int i = platform.indexOf("platform_version=") + "platform_version=".length();
			return platform.substring(i, i+3);
		}
		// Symbian OS 9.2
		if(checkClass("javax.microedition.amms.GlobalManager")) { // has amms
			return "3.1"; // s60v3 fp 1
		}
		// Symbian OS 9.1
		if(checkClass("javax.microedition.m2g.ScalableImage")) { // has m2g
			return "3.0"; // s60v3
		}
		if(checkClass("javax.crypto.Cipher")) { // has cryto api
			return "3.0";
		}
		if(System.getProperty("microedition.sip.version") != null) { // has sip
			return "3.0";
		}
		// Symbian OS 8.1
		if(checkClass("javax.microedition.xml.rpc.Element")) { // has xml api
			return "2.8"; // s60v2 fp3
		}
		// Symbian OS 8.0
		if(checkClass("javax.microedition.io.file.FileConnection")) { // has jsr 75
			return "2.6"; // s60v2 fp2
		}
		// Symbian OS 7.0
		if(checkClass("com.nokia.microedition.media.SystemProperties")) {
			return "2.0"; // s60v2
		}
		if(checkClass("javax.microedition.m3g.Graphics3D")) { // has m3g
			return "2.1"; // s60v2 fp1
		}
		if(checkClass("javax.microedition.pki.Certificate")) { // has pki
			return "2.1"; // s60v2 fp1
		}
		// Symbian OS 6.1
		if(checkClass("javax.wireless.messaging.Message")) { // has wma
			return "1.2"; // s60 v1.2
		}
		return "1.0"; // s60 v1.0
	}
	
	public static String getS40_version() { // not real version
		if(!isS40) {
			return null;
		}
		if(platform.startsWith("Nokia300/") || platform.startsWith("NokiaC3-01") || platform.startsWith("NokiaX3-02")) {
			return "6.1"; // 6th Edition FP1
		}
		isAsha = true;
		if(isAshaFullTouch()) {
			if(platform.startsWith("Nokia230") || platform.startsWith("Nokia5")) {
				return "9.0"; // Asha Platform
			}
		}
		if(checkClass("javax.microedition.sensor.SensorManager")) { // has jsr 256
			return "8.0"; // Java Runtime 2.0
		}
		if(isAshaTouchAndType() || isAshaNoTouch()) {
			if(checkClass("com.nokia.mid.payment.IAPClientPaymentManager")) {
				return "7.1"; // Java Runtime 1.1
			}
			return "7.0"; // Java Runtime 1.0
		}
		isAsha = false;
		if(checkClass("com.arm.cldc.mas.GlobalLock")) {
			return "6.0"; // 6th Edition SDK
		}
		if(checkClass("javax.microedition.location.LocationProvider")) { // has jsr 179
			return "6.0"; // 6th Edition SDK
		}
		if(checkClass("javax.microedition.content.ContentHandler")) { // has chapi
			if(System.getProperty("microedition.jtwi.version") != null) { // has jtwi
				return "6.0 Lite"; // 6th Edition Lite
			}
			return "5.1"; // 5th Edition FP1 SDK
		}
		if(System.getProperty("microedition.jtwi.version") != null) {
			return "5.1 Lite"; // 6th Edition FP1 Lite
		}
		if(checkClass("javax.microedition.amms.GlobalManager")) { // has amms
			return "5.0"; // 5th Edition SDK
		}
		if(checkClass("javax.crypto.Cipher")) { // has crypto api
			return "3.2"; // 3rd Edition FP2
		}
		if(checkClass("javax.xml.parsers")) { // has jsr 172 xml
			return "3.1"; // 3rd Edition FP1
		}
		if(checkClass("javax.microedition.xml.rpc.Element")) { // has jsr 172 rpc
			return "3.1"; // 3rd Edition FP1
		}
		if(checkClass("javax.microedition.m2g.ScalableGraphics")) { // has m2g
			return "3.0.1"; // 3rd Edition
		}
		if(checkClass("com.nokia.mid.pri.PriAccess")) {
			return "3.0"; // 3rd Edition SDK
		}
		if(checkClass("javax.microedition.io.file.FileConnection")) { // has jsr 75
			return "2.2"; // DP 2.0 SDK 6230i
		}
		if(checkClass("com.nokia.mid.impl.isa.io.GeneralSharedIO")) {
			return "2.1"; // DP 2.0 SDK 1.1
		}
		if(checkClass("javax.microedition.lcdui.game.GameCanvas")) { // has midp 2.0
			return "2.0"; // DP 2.0 SDK 1.0
		}
		if(checkClass("com.sun.midp.Main")) {
			return null; // 3410 / not s40
		}
		if(checkClass("javax.microedition.media.Manager")) { // has jsr 135
			return "1.2"; // 3300
		}
		if(checkClass("javax.wireless.messaging.MessageConnection")) { // has wma
			return "1.1";
		}
		return "1.0";
	}
	
	public static boolean isAshaFullTouch() {
		if(!isAsha) return false;
		if(System.getProperty("com.nokia.mid.ui.version") == null) return false;
		String s = platform.substring(5);
		if(s.startsWith("Asha5")) return true;
		char c1 = s.charAt(0);
		char c2 = s.charAt(1);
		char c3 = s.charAt(2);
		if((c1 != '2' && c1 != '3' && c1 != '5') || (c2 != '0' && c2 != '1' && c2 != '3')) return false;
		if(c1 == '5') return c3 != '0';
		if(c1 == '2') return c2 == '3';
		if(c1 == '3') return c2 == '0' ? c3 == '5' || c3 == '6' || c3 == '8' || c3 == '9' : c2 == '1' && (c3 == '0' || c3 == '1');
		return false;
	}
	
	public static boolean isAshaTouchAndType() {
		if(!isAsha) return false;
		String s = platform.substring(5);
		char c1 = s.charAt(0);
		char c2 = s.charAt(1);
		char c3 = s.charAt(2);
		if((c1 != '2' && c1 != '3') || c2 != '0') return false;
		if(c1 == '2') return c2 == '0' && (c3 == '2' || c3 == '3');
		if(c1 == '3') return c2 == '0' && (c3 == '0' || c3 == '3');
		return false;
	}
	
	public static boolean isAshaNoTouch() {
		if(!isAsha) return false;
		String s = platform.substring(5);
		char c1 = s.charAt(0);
		char c2 = s.charAt(1);
		char c3 = s.charAt(2);
		if((c1 != '2' && c1 != '3') || (c2 != '0' && c2 != '1')) return false;
		if(c1 == '2') return c2 == '0' ? c3 == '0' && c3 == '1' && c3 == '5' : c2 == '1' && c3 == '0';
		if(c1 == '3') return c2 == '0' && c3 == '2';
		return false;
	}

	// J2ME Loader check
	public static boolean isJ2ML() {
		return "Linux".equals(os) && "The Android Project".equals(vendor);
	}

	public static boolean isSamsung() {
		return platform.toLowerCase().startsWith("samsung") || platform.startsWith("GT-");
	}

	public static boolean isPhoneme() {
		return version != null && version.indexOf("phoneme") != -1;
	}

	public static boolean isNokia() {
		return platform.startsWith("Nokia");
	}

	public static boolean isSonyEricsson() {
		return System.getProperty("com.sonyericsson.java.platform") != null || platform.toLowerCase().startsWith("sonyericsson");
	}

	public static boolean isWTK() {
		return platform.startsWith("wtk") || platform.endsWith("wtk");
	}

	public static boolean isPlatformJ2ME() {
		return "j2me".equals(platform);
	}
	
	public static boolean isBlackBerry() {
		return platform.toLowerCase().startsWith("blackberry");
	}
	
	public static boolean checkClass(String s) {
		try {
			Class.forName(s);
			return true;
		} catch (Exception e) {
		}
		return false;
	}

}
