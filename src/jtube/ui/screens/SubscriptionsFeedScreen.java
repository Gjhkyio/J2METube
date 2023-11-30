/*
Copyright (c) 2023 Arman Jussupgaliyev

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
package jtube.ui.screens;

import java.util.Calendar;
import java.util.Date;
import java.util.Vector;

import cc.nnproject.json.JSONArray;
import cc.nnproject.json.JSONObject;
import jtube.App;
import jtube.Constants;
import jtube.Loader;
import jtube.LoaderThread;
import jtube.LocalStorage;
import jtube.Settings;
import jtube.models.ILoader;
import jtube.models.VideoModel;
import jtube.ui.Locale;
import jtube.ui.items.Label;

public class SubscriptionsFeedScreen extends NavigationScreen implements Runnable, Constants, ILoader {

	private int lastCount;
	private Vector allVideos = new Vector();
	private String[] subscriptions;
	private int idx;
	private Object lock = new Object();
	private Thread thread;
	private boolean loaded;

	public SubscriptionsFeedScreen() {
		super(Locale.s(TITLE_Subscriptions));
		menuOptions = new String[] {
				Locale.s(CMD_ChannelsList)
		};
	}
	
	protected void show() { 
		super.show();
		if((lastCount != LocalStorage.getSubsciptions().length || !loaded) && !busy) {
			thread = new Thread(this);
			thread.start();
			lastCount = LocalStorage.getSubsciptions().length;
		}
	}
	
	public void hide() {
		if(thread != null) {
			thread.interrupt();
		}
		clear();
		allVideos.removeAllElements();
		Loader.stop();
		super.hide();
	}

	public void run() {
		busy = true;
		loaded = false;
		idx = 0;
		subscriptions = LocalStorage.getSubsciptions();
		if(subscriptions == null || subscriptions.length == 0) {
			busy = false;
			return;
		}
		try {
			allVideos.removeAllElements();
			Loader.stop();
			Thread.sleep(100);
			for(int i = 0; i < subscriptions.length; i+=2) {
				Loader.add(this);
			}
			Loader.start();
			// wait till all channels load
			synchronized(lock) {
				lock.wait();
			}
			// sorting
			for (int i = 0; i < allVideos.size(); i++) {
				for (int j = i + 1; j < allVideos.size(); j++) {
					if (((JSONObject) allVideos.elementAt(i)).getLong("published") < ((JSONObject) allVideos.elementAt(j)).getLong("published")) {
						Object t = allVideos.elementAt(i);
						allVideos.setElementAt(allVideos.elementAt(j), i);
						allVideos.setElementAt(t, j);
					}
				}
			}
			Date lastDate = null;
			for(int i = 0; i < allVideos.size(); i++) {
				if(i > 100) break;
				JSONObject j = (JSONObject) allVideos.elementAt(i);
				VideoModel v = new VideoModel(j);
				Date date = new Date(j.getLong("published") * 1000L);
				if(lastDate == null || !dateEqual(date, lastDate)) {
					lastDate = date;
					add(new Label(dateStr(date), smallfont));
				}
				if(Settings.videoPreviews && !Settings.lazyLoad) Loader.add(v);
				add(v.makeListItem());
			}
			allVideos.removeAllElements();
			loaded = true;
		} catch (Exception e) {
			e.printStackTrace();
		}
		busy = false;
	}
	
	private static boolean dateEqual(Date a, Date b) {
		Calendar ca = Calendar.getInstance();
		ca.setTime(a);
		Calendar cb = Calendar.getInstance();
		cb.setTime(b);
		return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) && 
				ca.get(Calendar.MONTH) == cb.get(Calendar.MONTH) && 
				ca.get(Calendar.DAY_OF_MONTH) == cb.get(Calendar.DAY_OF_MONTH);
	}
	
	private static String dateStr(Date date) {
		Calendar c = Calendar.getInstance();
		c.setTime(date);
		return c(c.get(Calendar.DAY_OF_MONTH)) + "." + c(c.get(Calendar.MONTH)+1) + "." + c.get(Calendar.YEAR);
	}
	
	private static String c(int i) {
    	String s = String.valueOf(i);
    	if(s.length() < 2) s = "0" + s;
		return s;
	}

	public void load() {
		if(idx*2 >= subscriptions.length) return;
		try {
			String id;
			synchronized(subscriptions) {
				id = subscriptions[(idx++) * 2];
			}
			JSONObject r = (JSONObject) App.invApi("channels/" + id + "/latest?", "videos(" + VIDEO_FIELDS + ",published)");
			JSONArray j = r.getArray("videos");
			if(((LoaderThread) Thread.currentThread()).checkInterrupted()) {
				throw new RuntimeException("loader interrupt");
			}
			for(int k = 0; k < j.size(); k++) {
				if(k > 10) break;
				allVideos.addElement(j.get(k));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		if(idx*2 >= subscriptions.length) {
			synchronized(lock) {
				lock.notify();
			}
		}
	}
	
	protected void menuAction(int action) {
		switch(action) {
		case 0:
			ui.nextScreen(new SubscriptionsListScreen());
			break;
		}
	}

}
