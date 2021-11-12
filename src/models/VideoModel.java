package models;

import java.io.IOException;

import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.ImageItem;
import javax.microedition.lcdui.Item;
import javax.microedition.lcdui.ItemCommandListener;
import javax.microedition.lcdui.StringItem;

import App;
import Constants;
import InvidiousException;
import cc.nnproject.json.JSONArray;
import cc.nnproject.json.JSONObject;
import tube42.lib.imagelib.ImageUtils;

public class VideoModel implements ItemCommandListener, ILoader, Constants {

	private String title;
	private String videoId;
	private String author;
	private String authorId;
	private String description;
	private int viewCount;
	//private long published;
	private String publishedText;
	//private int lengthSeconds;
	private int likeCount;
	private int dislikeCount;

	private JSONArray videoThumbnails;
	private ImageItem imageItem;
	private Image img;
	private JSONArray authorThumbnails;

	private boolean extended;
	private boolean fromSearch;

	private ImageItem authorItem;

	// create model without parsing
	public VideoModel(String id) {
		videoId = id;
	}

	public VideoModel(JSONObject j) {
		this(j, false);
	}

	public VideoModel(JSONObject j, boolean extended) {
		parse(j, extended);
	}

	private void parse(JSONObject j, boolean extended) {
		this.extended = extended;
		videoId = j.getString("videoId");
		title = j.getNullableString("title");
		if(App.videoPreviews) {
			videoThumbnails = j.getNullableArray("videoThumbnails");
		} else {
			author = j.getNullableString("author");
		}
		if(extended) {
			viewCount = j.getInt("viewCount", 0);
			//lengthSeconds = j.getInt("lengthSeconds", 0);
			
			description = j.getNullableString("description");
			authorId = j.getNullableString("authorId");
			//published = j.getLong("published", 0);
			publishedText = j.getNullableString("publishedText");
			likeCount = j.getInt("likeCount", -1);
			dislikeCount = j.getInt("dislikeCount", -1);
			if(App.videoPreviews) authorThumbnails = j.getNullableArray("authorThumbnails");
		}
		j = null;
	}
	
	public VideoModel extend() throws InvidiousException, IOException {
		if(!extended) {
			parse((JSONObject) App.invApi("v1/videos/" + videoId + "?fields=" + VIDEO_EXTENDED_FIELDS), true);
		}
		return this;
	}

	public Item makeItemForList() {
		//if(imageItem != null) return imageItem;
		if(!App.videoPreviews) {
			StringItem i = new StringItem(author, title);
			i.addCommand(vOpenCmd);
			i.setDefaultCommand(vOpenCmd);
			i.setItemCommandListener(this);
			return i;
		}
		imageItem = new ImageItem(title, img, Item.LAYOUT_CENTER, null, ImageItem.BUTTON);
		imageItem.addCommand(vOpenCmd);
		imageItem.setDefaultCommand(vOpenCmd);
		imageItem.setItemCommandListener(this);
		return imageItem;
	}

	public ImageItem makeImageItemForPage() {
		imageItem = new ImageItem(null, img, Item.LAYOUT_CENTER, null);
		return imageItem;
	}

	public void loadImage() {
		if(img != null) return;
		if(videoThumbnails == null) return;
		if(imageItem == null) return;
		try {
			int w = getPreferredWidth();
			if (w <= 0) w = 220;
			String url = getThumbUrl(w);
			byte[] b = App.hproxy(url);
			img = Image.createImage(b, 0, b.length);
			int h = (int) ((float) w * ((float) img.getHeight() / (float) img.getWidth()));
			img = ImageUtils.resize(img, w, h);
			imageItem.setImage(img);
			videoThumbnails = null;
		} catch (Exception e) {
			e.printStackTrace();
		} catch (OutOfMemoryError e) {
			e.printStackTrace();
		}
	}

	private void loadAuthorImg() {
		if(authorThumbnails == null) return;
		if(authorItem == null || authorItem.getImage() != null) return;
			try {
			byte[] b = App.hproxy(getAuthorThumbUrl());
			authorItem.setImage(Image.createImage(b, 0, b.length));
			authorThumbnails = null;
		} catch (Exception e) {
			e.printStackTrace();
		} catch (OutOfMemoryError e) {
			e.printStackTrace();
		}
	}
	
	private int getPreferredWidth() {
		return (int) (App.width * 2F / 3F);
	}
	
	private String getThumbUrl(int tw) {
		int s = 0;
		int ld = 16384;
		for(int i = 0; i < videoThumbnails.size(); i++) {
			JSONObject j = videoThumbnails.getObject(i);
			int d = Math.abs(tw - j.getInt("width"));
			if (d < ld) {
				ld = d;
				s = i;
			}
		}
		return videoThumbnails.getObject(s).getString("url");
	}
	
	private String getAuthorThumbUrl() {
		if(!extended) return null;
		int s = 0;
		int ld = 16384;
		for(int i = 0; i < authorThumbnails.size(); i++) {
			JSONObject j = authorThumbnails.getObject(i);
			int d = Math.abs(VIDEOFORM_AUTHOR_IMAGE_HEIGHT - j.getInt("width"));
			if (d < ld) {
				ld = d;
				s = i;
			}
		}
		return authorThumbnails.getObject(s).getString("url");
	}

	public void commandAction(Command c, Item item) {
		if(c == vOpenCmd) {
			App.open(this);
		}
	}

	public String getTitle() {
		return title;
	}

	public String getVideoId() {
		return videoId;
	}

	public String getAuthor() {
		return author;
	}

	public String getAuthorId() {
		return authorId;
	}

	public String getDescription() {
		return description;
	}

	public int getViewCount() {
		return viewCount;
	}

	//public long getPublished() {
	//	return published;
	//}

	public String getPublishedText() {
		return publishedText;
	}

	//public int getLengthSeconds() {
	//	return lengthSeconds;
	//}

	public void load() {
		loadImage();
		if(extended) {
			loadAuthorImg();
		}
	}

	public void dispose() {
		videoThumbnails = null;
		img = null;
		if(imageItem != null) imageItem.setImage(null);
	}

	public void setFromSearch() {
		fromSearch = true;
	}
	
	public boolean isFromSearch() {
		return fromSearch;
	}

	public void disposeExtendedVars() {
		extended = false;
		authorId = null;
		description = null;
		publishedText = null;
		authorThumbnails = null;
	}

	public int getLikeCount() {
		return likeCount;
	}

	public int getDislikeCount() {
		return dislikeCount;
	}

	public Item makeAuthorItem() {
		if(!App.videoPreviews) {
			Item i = new StringItem(null, getAuthor());
			i.setLayout(Item.LAYOUT_LEFT | Item.LAYOUT_NEWLINE_BEFORE | Item.LAYOUT_2);
			return i;
		}
		authorItem = new ImageItem(null, null, Item.LAYOUT_LEFT, null);
		return authorItem;
	}

	public boolean isExtended() {
		return extended;
	}

}
