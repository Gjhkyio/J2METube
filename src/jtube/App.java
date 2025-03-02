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

import java.io.IOException;
import java.io.OutputStream;
import java.util.Hashtable;

import javax.microedition.io.Connector;
import javax.microedition.io.file.FileConnection;
import javax.microedition.lcdui.Alert;
import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;

import cc.nnproject.json.AbstractJSON;
import cc.nnproject.json.JSON;
import cc.nnproject.json.JSONArray;
import cc.nnproject.json.JSONException;
import cc.nnproject.json.JSONObject;
import cc.nnproject.ytapp.App2;
import jtube.ui.AppUI;
import jtube.ui.Locale;
import jtube.ui.UIScreen;
import jtube.ui.screens.SplashScreen;
import jtube.ui.screens.VideoScreen;
import midletintegration.MIDletIntegration;

public class App implements Constants, Runnable, CommandListener {
	
	public static App inst;
	public static App2 midlet;
	
	public static int startWidth;
	public static int startHeight;
	private static int workingProxy;
	
	private Object[] queuedTasks = new Object[30];
	private int queuedTasksIdx;
	private Object tasksLock = new Object();
	private Thread tasksThread = new Thread("Task Thread") {
		public void run() {
			try {
				while(midlet.running) {
					if(queuedTasks[0] == null) {
						synchronized (tasksLock) {
							tasksLock.wait();
						}
					}
					Object r = queuedTasks[0];
					synchronized(tasksLock) {
						System.arraycopy(queuedTasks, 1, queuedTasks, 0, queuedTasks.length - 1);
						queuedTasks[queuedTasks.length - 1] = null;
						queuedTasksIdx--;
					}
					try {
						if(r instanceof UIScreen)
							((UIScreen) r).hide();
						else if(r instanceof Runnable)
							((Runnable) r).run();
					} catch (Throwable e) {
					}
					Thread.sleep(1);
				}
			} catch (Exception e) {
			}
		}
	};

	private AppUI ui;
	private Thread uiThread;
	
	private SplashScreen splash;
	private String update;

	public void schedule(Object r) {
		queuedTasks[queuedTasksIdx++] = r;
		synchronized(tasksLock) {
			if(queuedTasksIdx == queuedTasks.length) {
				Object[] tmp = queuedTasks;
				queuedTasks = new Object[queuedTasks.length + 16];
				System.arraycopy(tmp, 0, queuedTasks, 0, tmp.length);
			}
			tasksLock.notify();
		}
	}

	public void cancel(Runnable r) {
		for(int i = 0; i < queuedTasks.length; i++) {
			if(queuedTasks[i] == r) {
				synchronized(tasksLock) {
					if(i < queuedTasks.length - 1) {
						System.arraycopy(queuedTasks, i+1, queuedTasks, i, queuedTasks.length - i);
					}
					queuedTasks[queuedTasks.length - 1] = null;
					queuedTasksIdx--;
				}
				return;
			}
		}
	}

	public void startApp() {
		if(!"JTube".equalsIgnoreCase(midlet.getAppProperty("MIDlet-Name"))
			|| !"nnproject".equalsIgnoreCase(midlet.getAppProperty("MIDlet-Vendor"))) {
			throw new RuntimeException();
		}
		
		PlatformUtils.samsungBuild = App.midlet.getAppProperty("JTube-Samsung-Build") != null;
		
		SplashScreen splash = new SplashScreen();
		Display.getDisplay(midlet).setCurrent(splash);
		App.startWidth = splash.getWidth();
		App.startHeight = splash.getHeight();
		
		String region = System.getProperty("user.country");
		if(region == null) {
			region = System.getProperty("user.region");
		}
		if(region == null) {
			region = System.getProperty("microedition.locale");
			if(region == null) {
				region = "US";
			} else if(region.length() == 5) {
				region = region.substring(3, 5);
			} else if(region.length() > 2 || region.equalsIgnoreCase("en")) {
				region = "US";
			}
		} else if(region.length() > 2) {
			region = region.substring(0, 2);
		}
		Settings.region = region.toUpperCase();
		
		tasksThread.start();
		Locale.init();
		Settings.loadConfig(splash);
		Locale.load();
		
		if(PlatformUtils.isBlackBerry() && !Settings.bbSet) {
			Settings.bbWifi = false;
			this.splash = splash;
			Alert a = new Alert("");
			a.setString("Network");
			a.addCommand(new Command("Wi-Fi", Command.OK, 4));
			a.addCommand(new Command("Cellular", Command.CANCEL, 5));
			a.setCommandListener(this);
			Display.getDisplay(midlet).setCurrent(a);
			return;
		}
		startUIThread();
	}
	
	private void startUIThread() {
		uiThread = new Thread(this, "UI Thread");
		uiThread.start();
	}
	
	public void run() {
		LocalStorage.init();
		initUI();
		Loader.init();
		new Thread() {
			public void run() {
				if(!checkStartArguments()) {
					ui.loadMain();
				}
				try {
					Thread.sleep(100);
					checkUpdate();
				} catch (Throwable e) {
				}
			}
		}.start();
		ui.run();
	}
	
	private void checkUpdate() {
		boolean b = false;
		try {
			JSONObject j = JSON.getObject(Util.getUtf(updateurl+
					"?v="+App.midlet.getAppProperty("MIDlet-Version")+
					"&l="+Locale.lang+
					"&s="+(App.midlet.getAppProperty("JTube-Samsung-Build") != null ? "1" : "0")+
					"&b="+(App.midlet.getAppProperty("JTube-BlackBerry-Build") != null ? "1" : "0")+
					"&p="+Util.url(PlatformUtils.platform)
					));
			if(j.getBoolean("update_available", false) && Settings.checkUpdates && !b) {
				update = j.getString("download_url");
				String msg = j.getString("message", Locale.s(Locale.TXT_NewUpdateAvailable));
				Alert a = new Alert("", "", null, AlertType.INFO);
				a.setString(msg);
				a.addCommand(new Command(Locale.s(Locale.CMD_Ignore), Command.EXIT, 2));
				a.addCommand(new Command(Locale.s(Locale.CMD_Download), Command.OK, 1));
				a.setCommandListener(this);
				ui.display(a);
			}
		} catch (Exception e) {}
	}
	
	private void initUI() {
		ui = new AppUI();
		ui.init();
	}

	public static byte[] getImageBytes(String s) throws IOException {
		if(s.startsWith("//")) s = "http:" + s;
		if(s.indexOf("ggpht.com") != -1 || s.indexOf("googleusercontent.com") != -1) {
			if(s.indexOf("//") != -1) s = s.substring(s.indexOf("//") + 2);
			s = "/ggpht" + s.substring(s.indexOf("/"));
		}
		if(s.startsWith("/")) s = Settings.inv + s.substring(1);
		if(Settings.useApiProxy) {
			s = Settings.apiProxy.concat("?u=").concat(Util.url(s));
		}
		return Util.get(s);
	}
	
	public static AbstractJSON invApi(String s) throws InvidiousException, IOException {
		return invApi(s, null, 0);
	}

	public static AbstractJSON invApi(String s, String fields) throws InvidiousException, IOException {
		return invApi(s, null, 0);
	}

	public static AbstractJSON invApi(String s, String fields, int tries) throws InvidiousException, IOException {
		String url = s;
		if (!s.endsWith("?")) s = s.concat("&");
		s += "region=" + (Settings.region != null ? Settings.region.toUpperCase() : "US");
		if (fields != null) {
			s = s + "&fields=" + fields + ",error,errorBacktrace,code,message";
		}
		s = Settings.inv + "api/v1/" + s;
		if (Settings.useApiProxy) {
			s = Settings.apiProxy + "?u=" + Util.url(s);
		}
		try {
			s = Util.getUtf(s);
		} catch (IOException e) {
			e.printStackTrace();
			throw new NetRequestException(e, url);
		}
		char c;
		if (s.length() == 0 || ((c = s.charAt(0)) != '{' && c != '[')) {
			if (tries > 0) {
				throw new NetRequestException(new IOException("Invalid response"), url);
			}
			changeInstance();
			return invApi(url, fields, ++tries);
		}
		AbstractJSON res;
		if (c == '{') {
			res = JSON.getObject(s);
			if (((JSONObject) res).has("code")) {
				throw new InvidiousException((JSONObject) res, ((JSONObject) res).getString("code") + ": " + ((JSONObject) res).getNullableString("message"), url, "");
			}
			if (((JSONObject) res).has("error")) {
				throw new InvidiousException((JSONObject) res, ((JSONObject) res).getNullableString("error"), url, "");
			}
		} else {
			res = JSON.getArray(s);
		}
		return res;
	}

	static JSONObject getVideoInfo(String id, String res) throws JSONException, IOException {
		boolean combined = res == null || res.charAt(0) != '_';
		String f = combined ? "formatStreams" : "adaptiveFormats";
		JSONObject j = (JSONObject) invApi("videos/"  + id + "?", f);
		JSONArray arr = j.getArray(f);
		if(j.size() == 0) {
			throw new RuntimeException("failed to get link for video: " + id);
		}
		int l = arr.size();
		if(combined) {
			JSONObject _144 = null;
			JSONObject _360 = null;
			JSONObject _720 = null;
			for(int i = 0; i < l; i++) {
				JSONObject o = arr.getObject(i);
				String q = o.getString("qualityLabel");
				if(q.startsWith("720p")) {
					_720 = o;
				} else if(q.startsWith("360p")) {
					_360 = o;
				} else if(q.startsWith("144p")) {
					_144 = o;
				}
			}
			JSONObject o = null;
			if(res == null) {
				if(_360 != null) {
					o = _360;
				} else if(_144 != null) {
					o = _144;
				} 
			} else if(res.equals("144p")) {
				if(_144 != null) {
					o = _144;
				}
				/* else if(_360 != null) {
					o = _360;
				} else if(other != null) {
					o = other;
				}*/
			} else if(res.equals("360p")) {
				if(_360 != null) {
					o = _360;
				} else if(_144 != null) {
					o = _144;
				} 
			} else if(res.equals("720p")) {
				if(_720 != null) {
					o = _720;
				} else if(_360 != null) {
					o = _360;
				} else if(_144 != null) {
					o = _144;
				} 
			}
			if(o == null) {
				throw new RuntimeException("Failed to find link for selected quality: " + res);
			}
			return o;
		} else {
			JSONObject r = null;
			int k = -1;
			if(res.equals("_audiolow"))
				k = Integer.MAX_VALUE;
			for(int i = 0; i < l; i++) {
				JSONObject o = arr.getObject(i);
				if(o.getString("type", "").startsWith("audio/mp4")) {
					if(res.equals("_audiolow")) {
						int n = o.getInt("bitrate", 0);
						if(n < k || n < 0) r = o;
					}
					if(res.equals("_audiohigh")) {
						int n = o.getInt("bitrate", 0);
						if(n > k || n < 0) r = o;
					}
				}
				if(res.equals("_240p")) {
					if(o.getString("qualityLabel", "").startsWith("240p") && o.getString("type", "").startsWith("video/mp4")) {
						r = o;
					}
				}
			}
			return r;
		}
	}

	public static String getVideoLink(JSONObject videoInfo, boolean forceProxy, int proxy) throws JSONException, IOException {
		if(videoInfo == null) throw new RuntimeException("not found");
		String url = videoInfo.getString("url");
		if(Settings.httpStream || forceProxy) {
			if (proxy == 0 || proxy == 4) {
				// Test proxies on auto mode
				if(workingProxy == 0) {
					// invidious
					try {
						String url2 = Settings.inv + url.substring(url.indexOf("/videoplayback")+1);
						if(Settings.useApiProxy) {
							url2 = Settings.serverstream + Util.url(url2);
						}
						if(Util.head(url2) >= 400) throw new Exception();
						workingProxy = 1;
						url = url2;
					} catch (Exception e) {
						// nnchan vpb
						try {
							String url2 = Settings.videoplaybackProxy + url.substring(url.indexOf("/videoplayback")+14);
							if(Util.head(url2) >= 400) throw new Exception();
							url = url2;
							workingProxy = 2;
						} catch (Exception e2) {
							// user url proxy
							url = Settings.serverstream + Util.url(url);
							workingProxy = 3;
						}
					}
					return url;
				}
				proxy = workingProxy;
			}
			switch(proxy) {
			case 1:
				url = Settings.inv + url.substring(url.indexOf("/videoplayback")+1);
				if(Settings.useApiProxy) {
					url = Settings.serverstream + Util.url(url);
				}
				break;
			case 2:
				url = Settings.videoplaybackProxy + url.substring(url.indexOf("/videoplayback")+14);
				break;
			case 3:
				url = Settings.serverstream + Util.url(url);
				break;
			}
		}
		return url;
	}
	
	public static void download(final String id, String name) {
		if(Settings.downloadDir == null || Settings.downloadDir.length() < 2) {
			Alert a = new Alert("");
			a.setString(Locale.s(Locale.TXT_DownloadDirWarning));
			a.addCommand(new Command(Locale.s(Locale.CMD_Settings), Command.OK, 3));
			a.setCommandListener(inst);
			displayAlert(a);
			return;
		}
		new Downloader(id, "360p", Settings.downloadDir, name).start();
	}
	
	private static String getUrl(String id) throws Exception {
		int proxy = Settings.playbackProxyVariant;
		if (proxy == 0) {
			return vpb3 + "v=" + id + "&i=360p&inv=" + Util.url(Settings.inv);
		} else {
			return getVideoLink(getVideoInfo(id, "360p"), false, proxy == 4 ? 0 : proxy);
		}
	}
	
	public static void watch(final String id) {
		Loader.stop();
		if(AppUI.inst.current instanceof VideoScreen) {
			AppUI.inst.current.busy = true;
		}
		try {
			switch (Settings.watchMethod) {
			case 0: {
				try {
					Util.platReq(getUrl(id));
				} catch (Exception e) {
					error(null, Errors.App_watch, e);
				}
				break;
			}
			case 1: {
				if(Settings.downloadDir == null || Settings.downloadDir.length() < 2) {
					Alert a = new Alert("");
					a.setString(Locale.s(Locale.TXT_DownloadDirWarning));
					a.addCommand(new Command(Locale.s(Locale.CMD_Settings), Command.OK, 3));
					a.setCommandListener(inst);
					displayAlert(a);
					if(AppUI.inst.current instanceof VideoScreen) {
						AppUI.inst.current.busy = false;
					}
					return;
				}

				String url = getUrl(id);
				
				if(PlatformUtils.isBada) {
					String file = "file:///Media/Videos/watch.ram";
					FileConnection fc = null;
					OutputStream o = null;
					try {
						fc = (FileConnection) Connector.open(file, 3);
						if (fc.exists())
							fc.delete();
						fc.create();
						o = fc.openDataOutputStream();
						o.write(url.getBytes("UTF-8"));
						o.flush();
						Util.platReq(file);

						if(AppUI.inst.current instanceof VideoScreen) {
							AppUI.inst.current.busy = false;
						}
						return;
					} catch (Exception e) {
					} finally {
						try {
							if (o != null)
								o.close();
							if (fc != null)
								fc.close();
						} catch (Exception e) {
						}
					}
				}
				
				String file = "file:///" + Settings.downloadDir;
				if (!file.endsWith("/") && !file.endsWith("\\"))
					file += "/";
				if (PlatformUtils.isSymbian3Based() || PlatformUtils.isBada) {
					file += "watch.ram";
				} else {
					Settings.watchMethod = 0;
					Util.platReq(url);
					break;
				}
				FileConnection fc = null;
				OutputStream o = null;
				try {
					fc = (FileConnection) Connector.open(file, 3);
					if (fc.exists())
						fc.delete();
					fc.create();
					o = fc.openDataOutputStream();
					o.write(url.getBytes("UTF-8"));
					o.flush();
				} finally {
					try {
						if (o != null)
							o.close();
						if (fc != null)
							fc.close();
					} catch (Exception e) {
					}
				}
				Util.platReq(file);
				break;
			}
			case 2: {
				Util.platReq("http://j2me.gjhkyioprojects.serv00.net/vplay.php?v=" + id : ""));
				break;
			}
			case 3: {
				Util.platReq(Settings.jtdlUrl + "?v=" + id + "&f=" + Settings.jtdlFormat +
						(Settings.jtdlPassword != null ? "&p=" + Util.url(Settings.jtdlPassword) : "")
						);
				break;
			}
			}
		} catch (Exception e) {
			if(e instanceof RuntimeException && "not found".equals(e.getMessage())) {
				inst.ui.msg("Selected quality is not available");

				if(AppUI.inst.current instanceof VideoScreen) {
					AppUI.inst.current.busy = false;
				}
				return;
			}
			error(null, Errors.App_watch, e);
		}
		if(AppUI.inst.current instanceof VideoScreen) {
			AppUI.inst.current.busy = false;
		}
	}
	
	public static String getThumbUrl(String id, int tw) {
		String s;
		if(tw < 120) {
			s = "default";
		} else if(tw < 480) {
			s = "mqdefault";
		} else if(tw < 640) {
			s = "hqdefault";
		} else if(tw < 720) {
			s = "sddefault";
		} else {
			s = "maxresdefault";
		}
		return "/vi/" + id + "/" + s + ".jpg";
	}

	public static String getThumbUrl(JSONArray arr, int tw) {
		JSONObject s = null;
		int ld = 16384;
		int l = arr.size();
		for(int i = 0; i < l; i++) {
			JSONObject j = arr.getObject(i);
			int d = Math.abs(tw - j.getInt("width"));
			if (d < ld) {
				ld = d;
				s = j;
			}
		}
		return s.getString("url");
	}

	public static String getSmallestThumbUrl(JSONArray arr) {
		JSONObject s = null;
		int ld = 16384;
		int l = arr.size();
		for(int i = 0; i < l; i++) {
			JSONObject j = arr.getObject(i);
			int d = j.getInt("width");
			if (d < ld) {
				ld = d;
				s = j;
			}
		}
		return s.getString("url");
	}

	public static void warn(Object o, String str) {
		String s = str + " \n\n" + getThreadInfo(o);
		Alert a = new Alert("", s, null, AlertType.WARNING);
		a.setTimeout(-2);
		displayAlert(a);
	}

	public static void error(Object o, String str) {
		String s = str + " \n" + CONTANT_DEVELOPER_STRING + " \n\n" + getThreadInfo(o);
		Alert a = new Alert("", s, null, AlertType.ERROR);
		displayAlert(a);
	}

	public static void error(Object o, int i, Throwable e) {
		if(e instanceof InvidiousException) {
			error(o, i, e.toString(), ((InvidiousException)e).toErrMsg());
			return;
		}
		if(e instanceof NetRequestException) {
			NetRequestException e2 = (NetRequestException) e;
			error(o, i, e2.getTheCause().toString(), "URL: " + e2.getUrl());
			return;
		}
		error(o, i, parseError(e, i), null);
	}

	public static void error(Object o, int i, String str) {
		error(o, i, str, null);
	}

	public static void error(Object o, int i, String str, String str2) {
		String s = str + " \n" + CONTANT_DEVELOPER_STRING + " " + EOL + EOL + "e: " + i + " \n" + 
				getThreadInfo(o) + 
				(str2 != null ? " \n" + str2 : "");
		Alert a = new Alert("", s, null, AlertType.ERROR);
		a.setTimeout(-2);
		displayAlert(a);
	}
	
	private static void displayAlert(Alert a) {
		if(inst.ui != null) {
			inst.ui.display(a);
			return;
		}
		Display.getDisplay(midlet).setCurrent(a);
	}
	
	private static String getThreadInfo(Object o) {
		Thread t = Thread.currentThread();
		return (o != null ? "at " + o.getClass().getName() + " " + EOL : "") + 
				"t: " + t.getName() +
				" (p: " + t.getPriority() + 
				" c: " + t.getClass() + ") " + EOL +
				Thread.activeCount();
	}
	
	public static boolean checkStartArguments() {
		try {
			if(MIDletIntegration.checkLaunch(Settings.autoStart)) {
				return parseStartArguments();
			}
		} catch (Exception e) {
		}
		return false;
	}
	
	public static boolean parseStartArguments() {
		Hashtable args = MIDletIntegration.getArguments(MIDletIntegration.getLaunchCommand());
		String s;
		if((s = (String) args.get("url")) != null && s.length() > 0) {
			try {
				openURL(Util.decodeURL(s));
				return true;
			} catch (IllegalArgumentException e) {
				return false;
			}
		} else if((s = (String) args.get("videoId")) != null && s.length() > 0) {
			inst.ui.openVideo(s);
			return true;
		} else if((s = (String) args.get("channelId")) != null && s.length() > 0) {
			inst.ui.openChannel(s);
			return true;
		} else if((s = (String) args.get("playlistId")) != null && s.length() > 0) {
			inst.ui.openPlaylist(s);
			return true;
		}
		return false;
	}

	public static void openURL(String url) {
		if(inst == null || inst.ui == null) {
			return;
		}
		if(url.startsWith("https")) url = url.substring(5);
		else if(url.startsWith("http")) url = url.substring(4);
		if(url.startsWith("://")) url = url.substring(3);
		if(url.startsWith("www")) url = url.substring(3);
		if(url.startsWith("m.")) url = url.substring(2);
		if(url.startsWith("youtu.be")) {
			int i = url.indexOf('/');
			if(i == -1)
				throw new IllegalArgumentException();
			url = url.substring(i + 1);
			if((i = url.indexOf('/')) != -1 || (i = url.indexOf('?')) != -1) {
				url = url.substring(0, i);
			}
			inst.ui.openVideo(url);
		} else if(url.startsWith("youtube.com") || 
				url.startsWith("iteroni.com") || 
				url.startsWith("inv.nadeko.net")) {
			int i = url.indexOf('/');
			if(i == -1)
				throw new IllegalArgumentException();
			url = url.substring(i + 1);
			if(url.startsWith("watch")) {
				i = url.indexOf("?v=");
				if(i == -1)
					throw new IllegalArgumentException();
				url = url.substring(i + 3);
				if((i = url.indexOf('&')) != -1) {
					url = url.substring(0, i);
				}
				inst.ui.openVideo(url);
			} else if(url.startsWith("embed")) {
				i = url.indexOf('/');
				if(i == -1)
					throw new IllegalArgumentException();
				url = url.substring(i + 1);
				if((i = url.indexOf('/')) != -1 || (i = url.indexOf('?')) != -1) {
					url = url.substring(0, i);
				}
				inst.ui.openVideo(url);
			} else if(url.startsWith("@")) {
				url = url.substring(url.indexOf('@'));
				if((i = url.indexOf('/')) != -1 || (i = url.indexOf('?')) != -1) {
					url = url.substring(0, i);
				}
				inst.ui.openChannel(url);
			} else if(url.startsWith("c")) {
				i = url.indexOf('/');
				if(i == -1)
					throw new IllegalArgumentException();
				url = url.substring(i + 1);
				if((i = url.indexOf('/')) != -1 || (i = url.indexOf('?')) != -1) {
					url = url.substring(0, i);
				}
				inst.ui.openChannel(url);
			} else if(url.startsWith("playlist")) {
				i = url.indexOf("?list=");
				if(i == -1)
					throw new IllegalArgumentException();
				url = url.substring(i + 6);
				if((i = url.indexOf('&')) != -1) {
					url = url.substring(0, i);
				}
				inst.ui.openPlaylist(url);
			}
		} else {
			throw new IllegalArgumentException();
		}
	}
	
	public static String parseError(Throwable exception, int code) {
		String s = exception.toString();
		// TODO
		return s;
	}

	public void commandAction(Command c, Displayable d) {
		switch(c.getPriority()) {
		case 1:
			try {
				if(App.midlet.platformRequest(update)) {
					App.midlet.notifyDestroyed();
					return;
				}
			} catch (Exception e) {}
		case 2:
			update = null;
			ui.display(null);
			return;
		case 3:
			AppUI.inst.showSettings();
			return;
		case 4:
			Settings.bbWifi = true;
		case 5:
			Display.getDisplay(App.midlet).setCurrent(splash);
			splash = null;
			Settings.saveConfig();
			startUIThread();
			return;
		}
	}

	public static void changeInstance() throws IOException {
		JSONObject j = JSON.getObject(Util.getUtf(instancesurl + "?current=" + Util.url(Settings.inv)));
		Settings.inv = j.getString("url");
		try {
			Settings.saveConfig();
		} catch (Exception ignored) {}
	}

}
