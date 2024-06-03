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
package jtube.models;

import java.io.IOException;

import javax.microedition.lcdui.Image;

import cc.nnproject.json.JSONObject;
import jtube.App;
import jtube.Constants;
import jtube.InvidiousException;
import jtube.Loader;
import jtube.LocalStorage;
import jtube.Settings;
import jtube.Util;
import jtube.ui.AppUI;
import jtube.ui.IModelScreen;
import jtube.ui.UIItem;
import jtube.ui.UIScreen;
import jtube.ui.items.ChannelItem;
import jtube.ui.items.VideoItem;
import jtube.ui.items.VideoPreview;
import jtube.ui.screens.PlaylistScreen;
import jtube.ui.screens.VideoScreen;
import tube42.lib.imagelib.ImageUtils;

public class VideoModel extends AbstractModel implements ILoader, Constants, Runnable {

	public String title;
	public String videoId;
	public String author;
	public String authorId;
	public String description;
	public int viewCount;
	public String publishedText;
	public int lengthSeconds;
	public int likeCount;
	public String playlistId;
	private int subCount;

	public int imageWidth;
	private String authorThumbnailUrl;

	public boolean extended;
	public boolean fromSearch;
	public boolean fromPlaylist;
	
	private VideoItem item;
	private VideoPreview prevItem;
	private ChannelItem channelItem;
	
	private UIScreen containerScreen;
	
	public int index = -1;
	private boolean imgLoaded;
	
	private byte[] tempImgBytes;
	
	public boolean loaded;

	public VideoModel(String id) {
		videoId = id;
	}

	public VideoModel(JSONObject j) {
		this(j, false);
	}

	public VideoModel(JSONObject j, boolean extended) {
		parse(j, extended);
	}

	public VideoModel(JSONObject j, UIScreen s) {
		this(j, false);
		this.containerScreen = s;
		this.fromPlaylist = s instanceof PlaylistScreen;
	}

	private void parse(JSONObject j, boolean extended) {
		this.extended = extended;
		videoId = j.getString("videoId");
		title = j.getNullableString("title");
		author = j.getNullableString("author");
		authorId = j.getNullableString("authorId");
		lengthSeconds = j.getInt("lengthSeconds", 0);
		viewCount = j.getInt("viewCount", 0);
		publishedText = j.getNullableString("publishedText");
		if(extended) {
			subCount = j.getInt("subCount", 0);
			description = j.getNullableString("description");
			likeCount = j.getInt("likeCount", -1);
			if(Settings.videoPreviews && j.has("authorThumbnails")) {
				authorThumbnailUrl = App.getThumbUrl(j.getArray("authorThumbnails"), 36);
			}
		}
		j = null;
	}
	
	public VideoModel extend() throws InvidiousException, IOException {
		if(!extended) {
			try {
				parse((JSONObject) App.invApi("videos/" + videoId + "?", VIDEO_EXTENDED_FIELDS + (Settings.videoPreviews ? ",authorThumbnails" : "")), true);
			} catch (Exception ignored) {}
			try {
				JSONObject j = (JSONObject) App.invApi("channels/" + authorId + "?", "subCount");
				if(j.has("subCount"))
					subCount = j.getInt("subCount", 0);
			} catch (Exception e) {}
		}
		return this;
	}
	
	public Image customResize(Image img) {
		return resize(img, false, 0);
	}
	
	public Image resize(Image img, boolean prev, int w) {
		float iw = img.getWidth();
		float ih = img.getHeight();
		Util.gc();
		float f = iw / ih;
		int sw = AppUI.inst.getWidth();
		if(f == 4F / 3F && (sw > 480 && sw > AppUI.inst.getHeight())) {
			// cropping to 16:9
			float ch = iw * (9F / 16F);
			int chh = (int) ((ih - ch) / 2F);
			img = ImageUtils.crop(img, 0, chh, img.getWidth(), (int) (ch + chh));
			iw = img.getWidth();
			ih = img.getHeight();
		}
		float nw = (float) (prev ? w : imageWidth);
		int nh = (int) (nw * (ih / iw));
		img = ImageUtils.resize(img, (int)nw, nh);
		return img;
	}
	
	public Image previewResize(int w, Image img) {
		return resize(img, true, w);
	}

	public void loadImage() {
		if(imgLoaded) return;
		imgLoaded = true;
		int w = AppUI.inst.getWidth();
		if(!extended && Settings.smallPreviews) {
			w /= 3;
		}
		if(imageWidth == 0) imageWidth = w;
		String thumbnailUrl = App.getThumbUrl(videoId, w);
		if(item == null && prevItem == null && !extended) return;
		try {
			byte[] b = App.getImageBytes(thumbnailUrl);
			if(prevItem != null && extended) {
				Image img = Image.createImage(b, 0, b.length);
				b = null;
				Util.gc();
				prevItem.setImage(img);
			} else if(Settings.rmsPreviews) {
				if(Settings.isLowEndDevice()) {
					LocalStorage.cacheThumbnail(videoId, b);
					Image img = Image.createImage(b, 0, b.length);
					b = null;
					Util.gc();
					item.setImage(customResize(img));
					b = null;
				} else {
					tempImgBytes = b;
					App.inst.schedule(this);
					Image img = Image.createImage(b, 0, b.length);
					Util.gc();
					item.setImage(customResize(img));
				}
			} else {
				Image img = Image.createImage(b, 0, b.length);
				b = null;
				Util.gc();
				if(item != null) {
					item.setImage(customResize(img));
				}
			}
			thumbnailUrl = null;
			Util.gc();
		} catch (NullPointerException e) {
		} catch (IllegalArgumentException e) {
		} catch (RuntimeException e) {
			throw e;
		} catch (Exception e) {
		} catch (OutOfMemoryError e) {
			Util.gc();
			Loader.stop();
			App.warn(this, "Not enough memory to load video previews!");
		}
	}

	private void loadAuthorImg() {
		if(authorThumbnailUrl == null) return;
		try {
			_loadAuthorImg();
		} catch (IllegalArgumentException e) {
			if(e.toString().indexOf("format") != -1) {
				try {
					_loadAuthorImg();
				} catch (Throwable e2) {
				}
			}
		} catch (Throwable e) {
		}
	}

	private void _loadAuthorImg() throws Exception {
		byte[] b = App.getImageBytes(authorThumbnailUrl);
		authorThumbnailUrl = null;
		channelItem.setImage(ChannelItem.roundImage(ImageUtils.resize(Image.createImage(b, 0, b.length), 36, 36)));
	}
	
	public void setIndex(int i) {
		this.index = i;
	}

	public void load() {
		loaded = true;
		try {
			loadImage();
			if(extended) {
				loadAuthorImg();
			}
		} catch (RuntimeException e) {
			throw e;
		} catch (Exception e) {
		}
	}
	
	public void unload() {
		imgLoaded = false;
		loaded = false;
	}

	public void dispose() {
	}

	public void disposeExtendedVars() {
		imgLoaded = false;
		loaded = false;
		extended = false;
		authorId = null;
		description = null;
		publishedText = null;
		authorThumbnailUrl = null;
		channelItem = null;
		prevItem = null;
	}

	// Cache image to RMS
	public void run() {
		if(tempImgBytes != null) {
			LocalStorage.cacheThumbnail(videoId, tempImgBytes);
			tempImgBytes = null;
		}
	}

	public UIItem makeListItem() {
		return item = new VideoItem(this);
	}

	public UIItem makePreviewItem() {
		Image img = null;
		loaded = false;
		imgLoaded = false;
		/*if(item != null) {
			img = item.getImage();
		}*/
		if(Settings.rmsPreviews && Settings.videoPreviews) {
			try {
				img = LocalStorage.loadAndCacheThumnail(videoId, App.getThumbUrl(videoId, AppUI.inst.getWidth()));
			} catch (IOException e) {
			}
		}
		return prevItem = new VideoPreview(this, img);
	}

	public IModelScreen makeScreen() {
		return new VideoScreen(this);
	}
	
	public ChannelItem makeChannelItem() {
		return channelItem = (ChannelItem) new ChannelModel(authorId, author, null, subCount).makeVideoItem();
	}

	public void setContainerScreen(UIScreen s) {
		this.containerScreen = s;
		this.fromPlaylist = s instanceof PlaylistScreen;
		if(fromPlaylist) {
			this.playlistId = ((PlaylistModel)((PlaylistScreen)s).getModel()).playlistId;
		}
	}

	public UIScreen getContainerScreen() {
		return containerScreen;
	}

}
