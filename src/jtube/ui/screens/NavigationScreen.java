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
package jtube.ui.screens;

import java.io.IOException;

import javax.microedition.lcdui.Alert;
import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.TextBox;
import javax.microedition.lcdui.TextField;

import cc.nnproject.json.JSONArray;
import cc.nnproject.json.JSONObject;
import cc.nnproject.keyboard.Keyboard;
import cc.nnproject.keyboard.KeyboardConstants;
import cc.nnproject.keyboard.KeyboardListener;
import jtube.App;
import jtube.Loader;
import jtube.PlatformUtils;
import jtube.RunnableTask;
import jtube.Settings;
import jtube.Util;
import jtube.ui.AbstractListScreen;
import jtube.ui.AppUI;
import jtube.ui.Commands;
import jtube.ui.JTubeCanvas;
import jtube.ui.Locale;
import jtube.ui.SearchSuggestionsThread;
import jtube.ui.UIItem;
import jtube.ui.UIScreen;
import jtube.ui.items.VideoItem;
import jtube.ui.nokia.DirectFontUtil;
import jtube.ui.nokia.TextEditorInst;
import jtube.ui.nokia.TextEditorListener;
import jtube.ui.nokia.TextEditorUtil;

public abstract class NavigationScreen extends AbstractListScreen implements TextEditorListener, CommandListener, Commands, KeyboardListener {

	private static final Command textOkCmd = new Command(Locale.s(CMD_Search), Command.OK, 1);

	private static final Font searchFont = Font.getFont(0, 0, Font.SIZE_SMALL);

	private static final Font softFont = Font.getFont(0, Font.STYLE_BOLD, Font.SIZE_SMALL);
	
	private static Image searchImg;
	private static Image backImg;
	private static Image menuImg;
	private static Image homeImg;
	private static Image homeSelImg;
	private static Image subsImg;
	private static Image subsSelImg;
//	private static Image libImg;
//	private static Image libSelImg;

	private static boolean init;
	protected static boolean topBar;
	private static boolean amoledImgs;
	private static boolean addOk;

	protected static Keyboard keyboard;
	protected static TextEditorInst editor;

	private static SearchSuggestionsThread suggestionsThread;

	protected boolean wasHidden;

	private int topBarHeight = 48;
	private int softBarHeight = smallfontheight+4;
	
	protected String searchText = "";

	private int lastW;
	private int lastH;

	private boolean editorHidden = true;

	private boolean search;

	protected String[] menuOptions;
	private int menuSelection;
	private boolean menu;
	private int menuW;
	private int menuH;
	
	protected boolean hasSearch;

	private String[] searchSuggestions;

	private String[] itemOptions;
	private boolean itemMenu;
	private int itemMenuH;
	private UIItem item;

	private static boolean qwerty;
	
	protected NavigationScreen(String label) {
		super(label);
		init();
		hasSearch = true;
		menuOptions = new String[] {
				Locale.s(CMD_Settings),
				Locale.s(CMD_FuncMenu)
		};
	}
	
	private static void init() {
		if(init) return;
		init = true;
		topBar = ui.getCanvas().hasPointerEvents();
		try {
			if(topBar) {
				loadImages();
			}
			if(Settings.keyboard != 2 && topBar) {
				initKeyboard();
			}
		} catch (Exception e) {
		}
		addOk = !topBar && PlatformUtils.isS40;
		if(topBar) {
			suggestionsThread = new SearchSuggestionsThread();
			suggestionsThread.start();
		}
	}
	
	private static void loadImages() throws IOException {
		searchImg = Image.createImage("/search.png");
		backImg = Image.createImage("/back.png");
		menuImg = Image.createImage("/menu.png");
		homeImg = Image.createImage("/home.png");
		homeSelImg = Image.createImage("/homesel.png");
		subsImg = Image.createImage("/subs.png");
		subsSelImg = Image.createImage("/subssel.png");
//		libImg = Image.createImage("/lib.png");
//		libSelImg = Image.createImage("/libsel.png");
		if(Settings.amoled) {
			amoledImgs = true;
			searchImg = Util.invert(searchImg);
			backImg = Util.invert(backImg);
			menuImg = Util.invert(menuImg);
			homeImg = Util.invert(homeImg);
			homeSelImg = Util.invert(homeSelImg);
			subsImg = Util.invert(subsImg);
			subsSelImg = Util.invert(subsSelImg);
//			libImg = Util.invert(libImg);
//			libSelImg = Util.invert(libSelImg);
		}
	}
	
	private static void initKeyboard() {
		if(Settings.keyboard == 0 && TextEditorUtil.isSupported()) {
			if(editor != null) return;
			editor = TextEditorUtil.createTextEditor("", 100, TextField.ANY, 24, 24);
			editor.setForegroundColor(AppUI.getColor(COLOR_MAINFG) | 0xFF000000);
			editor.setBackgroundColor(AppUI.getColor(COLOR_TOPBAR_BG) | 0xFF000000);
			Font f = Font.getDefaultFont();
			if(DirectFontUtil.isSupported() && App.startWidth >= 360) {
				f = DirectFontUtil.getFont(0, 0, 23, 0);
			}
			editor.setFont(f);
		} else {
			if(keyboard != null) return;
			JTubeCanvas c = ui.getCanvas();
			keyboard = Keyboard.getKeyboard(c, false, c.width, c.height);
			keyboard.setTextFont(searchFont);
			keyboard.setTextColor(AppUI.getColor(COLOR_MAINFG));
			keyboard.setTextHintColor(AppUI.getColor(COLOR_GRAYTEXT));
			keyboard.setCaretColor(AppUI.getColor(COLOR_MAINFG));
			keyboard.setTextHint(Locale.s(TXT_SearchHint));
			keyboard.setLanguages(Settings.inputLanguages);
			ui.getCanvas().setKeyboard(keyboard);
			qwerty = keyboard.getPhysicalKeyboardType() != KeyboardConstants.PHYSICAL_KEYBOARD_PHONE_KEYPAD;
		}
	}

	public void hide() {
		wasHidden = true;
		search = false;
		if(editor != null && !editorHidden && editor.isVisible() && topBar) {
			editorHidden = true;
			searchText = "";
			editor.setFocus(false);
			editor.setVisible(false);
			editor.setParent(null);
		} else if(keyboard != null && keyboard.isVisible()) {
			keyboard.reset();
			keyboard.hide();
		}
		if(!Settings.videoPreviews) return;
		for(int i = 0; i < items.size(); i++) {
			Object o = items.elementAt(i);
			if(o instanceof VideoItem) {
				((VideoItem)o).unload();
			}
		}
	}
	
	protected void show() {
		super.show();
		if(Settings.amoled != amoledImgs && topBar) {
			try {
				loadImages();
			} catch (Exception e) {
			}
			amoledImgs = Settings.amoled;
		}
		ui.removeCommands();
		if(!Settings.fullScreen) {
			if(menuOptions == null) {
				ui.addCommand(menuCmd);
				ui.addCommand(backCmd);
			} else if(this instanceof HomeScreen) {
				ui.addCommand(exitCmd);
				ui.addCommand(menuCmd);
			} else {
				ui.addCommand(backCmd);
				ui.addCommand(optsCmd);
			}
			if(addOk) {
				ui.addCommand(okCmd);
			}
		}
	}
	
	public void canvasFocused() {
		if(!Settings.videoPreviews) return;
		for(int i = 0; i < items.size(); i++) {
			Object o = items.elementAt(i);
			if(!(o instanceof VideoItem) || ((UIItem)o).hidden) continue;
			((VideoItem)o).onHide();
		}
	}
	
	private void setEditorPositions() {
		if(editor != null && editor.isVisible()) {
			editor.setSize(lastW-98, 24);
			editor.setPosition(50, 12);
		}
	}
	
	protected void paint(Graphics g, int w, int h) {
		if(lastW != w || lastH != h) {
			lastW = w;
			lastH = h;
			menuW = w-w/4;
			if(menuOptions != null) {
				menuH = (menuOptions.length + (itemOptions != null ? itemOptions.length : 0)) * (mediumfontheight+8);
			}
			setEditorPositions();
		}
		if(keyboard != null && keyboard.isVisible()) {
			h -= keyboard.paint(g, w, h);
			g.setClip(0, 0, w, h);
		}
		if(search || topBar) {
			g.translate(0, topBarHeight);
			if(!search) _paint(g, w, h-(this instanceof VideoScreen ? topBarHeight : topBarHeight*2));
			g.translate(0, -topBarHeight);
			// top bar
			g.setColor(AppUI.getColor(COLOR_TOPBAR_BG));
			g.fillRect(0, 0, w, topBarHeight);
			g.setColor(AppUI.getColor(COLOR_TOPBAR_BORDER));
			g.drawLine(0, topBarHeight, w, topBarHeight);
			
			if(search) {
				if(topBar) {
					g.drawImage(searchImg, w - 36, 12, 0);
					g.drawImage(backImg, 12, 12, 0);
					if(searchSuggestions != null) {
						int sy = 50;
						g.setColor(AppUI.getColor(COLOR_GRAYTEXT));
						g.setFont(smallfont);
						for(int i = 0; i < searchSuggestions.length; i++) {
							if(searchSuggestions[i] != null)
								g.drawString(Util.getOneLine(searchSuggestions[i], smallfont, w-16), 8, sy+((48-smallfontheight) >> 1), 0);
							sy += 48;
							g.drawLine(0, sy, w, sy);
						}
					}
				} else {
					g.setColor(AppUI.getColor(COLOR_SOFTBAR_BG));
					g.fillRect(0, h-softBarHeight, w, softBarHeight);
					g.setColor(AppUI.getColor(COLOR_TOPBAR_BORDER));
					g.drawLine(0, h-softBarHeight, w, h-softBarHeight);
					g.setColor(AppUI.getColor(COLOR_SOFTBAR_FG));
					g.setFont(softFont);
					boolean b = searchText.length() > 0;
					String s1 = Locale.s(CMD_FullEdit);
					String s2 = Locale.s(b && !qwerty ? CMD_Clean : CMD_Cancel);
					g.drawString(s1, 2, h-2, Graphics.BOTTOM | Graphics.LEFT);
					g.drawString(s2, w-2, h-2, Graphics.BOTTOM | Graphics.RIGHT);
					if(b) g.drawString(Locale.s(CMD_Search), w >> 1, h-2, Graphics.BOTTOM | Graphics.HCENTER);
				}
				if(editor == null || editorHidden) {
					if(keyboard != null && keyboard.isVisible()) {
						keyboard.drawTextBox(g, topBar ? 50 : 0, 0, w - (topBar ? 100 : 0), topBarHeight);
						keyboard.drawOverlay(g);
					} else {
						g.setFont(searchFont);
						String s;
						if(searchText.length() > 0) {
							s = searchText;
							g.setColor(AppUI.getColor(COLOR_MAINFG));
							int ww = w-(topBar ? 100 : 0);
							if(searchFont.stringWidth(s) > ww) {
								while(searchFont.stringWidth(s = s.substring(1)) >= ww);
							}
						} else {
							g.setColor(AppUI.getColor(COLOR_GRAYTEXT));
							s = Locale.s(TXT_SearchHint);
						}
						g.drawString(s, topBar ? 50 : 0, (topBarHeight - searchFont.getHeight()) >> 1, 0);
					}
				}
			} else {
				if(!(this instanceof VideoScreen)) {
					g.setColor(AppUI.getColor(COLOR_TOPBAR_BG));
					g.fillRect(0, h-topBarHeight, w, topBarHeight);
					g.setColor(AppUI.getColor(COLOR_TOPBAR_BORDER));
					g.drawLine(0, h-topBarHeight, w, h-topBarHeight);
		
					int f = w / 2;
					g.drawImage(ui.currentTab == 0 ? homeSelImg : homeImg, (f-24) >> 1, h-36, 0);
					g.drawImage(ui.currentTab == 1 ? subsSelImg : subsImg, ((f-24) >> 1) + f, h-36, 0);
//					g.drawImage(ui.currentTab == 2 ? libSelImg : libImg, ((f-24) >> 1) + f + f, h-36, 0);
				}
				if(Settings.fullScreen) {
					int xx = 4;
					if(!(this instanceof HomeScreen || this instanceof SubscriptionsFeedScreen)) {
						g.drawImage(backImg, 12, 12, 0);
						xx += 48;
					}
					if(!(this instanceof VideoScreen)) {
						String s = label;
						if(s != null && s.length() > 0) {
							int ww = w-96-xx;
							if(mediumfont.stringWidth(s) >= ww) {
								g.setFont(smallfont);
								s = Util.getOneLine(s, smallfont, ww);
							} else {
								g.setFont(mediumfont);
							}
							g.setColor(AppUI.getColor(COLOR_MAINFG));
							g.drawString(s, xx, (48-g.getFont().getHeight()) >> 1, 0);
						}
					}
				}
				if(menuOptions != null) {
					g.drawImage(menuImg, w - 36, 12, 0);
				}
				if(hasSearch) {
					g.drawImage(searchImg, w - 84, 12, 0);
				}
			}
			if(menu) {
				int xx = (w-menuW) >> 1;
				int yy = (h-menuH) >> 1;
				g.setFont(mediumfont);
				g.setColor(AppUI.getColor(COLOR_MAINBG));
				g.fillRect(xx, yy, menuW, menuH);
				int y2 = yy;
				if(itemOptions != null) {
					for(int i = 0; i < itemOptions.length; i++) {
						g.setColor(AppUI.getColor(COLOR_ITEMBORDER));
						g.drawLine(xx+4, yy, xx+menuW-8, yy);
						g.setColor(AppUI.getColor(COLOR_MAINFG));
						g.drawString(Util.getOneLine(itemOptions[i], mediumfont, menuW-8), xx + 4, yy+4, 0);
						yy += 8 + mediumfontheight;
					}
				}
				for(int i = 0; i < menuOptions.length; i++) {
					g.setColor(AppUI.getColor(COLOR_ITEMBORDER));
					g.drawLine(xx+4, yy, xx+menuW-8, yy);
					g.setColor(AppUI.getColor(COLOR_MAINFG));
					g.drawString(Util.getOneLine(menuOptions[i], mediumfont, menuW-8), xx + 4, yy+4, 0);
					yy += 8 + mediumfontheight;
				}
				g.setColor(AppUI.getColor(COLOR_MAINBORDER));
				g.drawRect(xx, y2, menuW, menuH);
			} else if(itemMenu) {
				int xx = (w-menuW) >> 1;
				int yy = (h-itemMenuH) >> 1;
				g.setFont(mediumfont);
				g.setColor(AppUI.getColor(COLOR_MAINBG));
				g.fillRect(xx, yy, menuW, itemMenuH);
				int y2 = yy;
				for(int i = 0; i < itemOptions.length; i++) {
					g.setColor(AppUI.getColor(COLOR_ITEMBORDER));
					g.drawLine(xx+4, yy, xx+menuW-8, yy);
					g.setColor(AppUI.getColor(COLOR_MAINFG));
					g.drawString(Util.getOneLine(itemOptions[i], mediumfont, menuW-8), xx + 4, yy+4, 0);
					yy += 8 + mediumfontheight;
				}
				g.setColor(AppUI.getColor(COLOR_MAINBORDER));
				g.drawRect(xx, y2, menuW, itemMenuH);
			}
			return;
		}
		if(Settings.fullScreen) {
			_paint(g, w, h-softBarHeight);
			if(!busy) {
				if(menu) {
					int xx = (w-menuW) >> 1;
					int yy = Math.max(0, (h-softBarHeight-menuH) >> 1);
					g.setFont(mediumfont);
					g.setColor(AppUI.getColor(COLOR_MAINBG));
					int ih = 8 + mediumfontheight;
					if(yy+ih*(menuSelection+1) > h-softBarHeight) {
						for(int i = 0; i < menuSelection+1; i++) {
							if(yy+ih*(i+1) > h-softBarHeight) {
								yy-=ih;
							}
						}
					}
					g.fillRect(xx, yy, menuW, menuH);
					int y2 = yy;
					if(itemOptions != null) {
						for(int i = 0; i < itemOptions.length; i++) {
							g.setColor(AppUI.getColor(COLOR_MAINFG));
							if(menuSelection == i) {
								g.fillRect(xx, yy, 2, ih);
							}
							g.drawString(itemOptions[i], xx + 4, yy+4, 0);
							yy += ih;
						}
					}
					for(int i = 0; i < menuOptions.length; i++) {
						g.setColor(AppUI.getColor(COLOR_MAINFG));
						if(itemOptions != null ? menuSelection == i + itemOptions.length: menuSelection == i) {
							g.fillRect(xx, yy, 2, ih);
						}
						g.drawString(menuOptions[i], xx + 4, yy+4, 0);
						yy += ih;
					}
					g.setColor(AppUI.getColor(COLOR_MAINBORDER));
					g.drawRect(xx, y2, menuW, menuH);
				}
				g.setColor(AppUI.getColor(COLOR_SOFTBAR_BG));
				g.fillRect(0, h-softBarHeight, w, softBarHeight);
				g.setColor(AppUI.getColor(COLOR_TOPBAR_BORDER));
				g.drawLine(0, h-softBarHeight, w, h-softBarHeight);
				g.setColor(AppUI.getColor(COLOR_SOFTBAR_FG));
				g.setFont(softFont);
				if(!menu) {
					g.drawString(Locale.s(CMD_Func), 2, h-2, Graphics.BOTTOM | Graphics.LEFT);
				}
				if(!(this instanceof SubscriptionsFeedScreen && ui.currentTab == 1)) {
					if(menu || !(this instanceof HomeScreen)) {
						g.drawString(Locale.s(CMD_Back), w-2, h-2, Graphics.BOTTOM | Graphics.RIGHT);
					} else {
						g.drawString(Locale.s(CMD_Exit), w-2, h-2, Graphics.BOTTOM | Graphics.RIGHT);
					}
				}
				UIItem ci = cItem;
				if(ci != null) {
					if(ci.getOKLabel() > 0) {
						g.drawString(Locale.s(ci.getOKLabel()), w >> 1, h-2, Graphics.BOTTOM | Graphics.HCENTER);
					}
				}
			}
		} else {
			_paint(g, w, h);
			if(menu && !busy) {
				int xx = (w-menuW) >> 1;
				int yy = Math.max(0, (h-softBarHeight-menuH) >> 1);
				g.setFont(mediumfont);
				g.setColor(AppUI.getColor(COLOR_MAINBG));
				int ih = 8 + mediumfontheight;
				if(yy+ih*(menuSelection+1) > h-softBarHeight) {
					for(int i = 0; i < menuSelection+1; i++) {
						if(yy+ih*(i+1) > h-softBarHeight) {
							yy-=ih;
						}
					}
				}
				g.fillRect(xx, yy, menuW, menuH);
				int y2 = yy;
				if(itemOptions != null) {
					for(int i = 0; i < itemOptions.length; i++) {
						g.setColor(AppUI.getColor(COLOR_MAINFG));
						if(menuSelection == i) {
							g.fillRect(xx, yy, 2, ih);
						}
						g.drawString(itemOptions[i], xx + 4, yy+4, 0);
						yy += ih;
					}
				}
				for(int i = 0; i < menuOptions.length; i++) {
					g.setColor(AppUI.getColor(COLOR_MAINFG));
					if(itemOptions != null ? menuSelection == i + itemOptions.length: menuSelection == i) {
						g.fillRect(xx, yy, 2, ih);
					}
					g.drawString(menuOptions[i], xx + 4, yy+4, 0);
					yy += ih;
				}
				g.drawRect(xx, y2, menuW, itemMenuH);
			}
		}
	}
	
	private void _paint(Graphics g, int w, int h) {
		if(busy) {
			if(editor != null && editor.isVisible()) {
				editor.setVisible(false);
			}
			g.setColor(AppUI.getColor(COLOR_MAINBG));
			g.fillRect(0, 0, w, h);
			g.setColor(AppUI.getColor(COLOR_MAINFG));
			String s = Locale.s(TITLE_Loading) + "...";
			g.setFont(smallfont);
			g.drawString(s, (w-smallfont.stringWidth(s)) >> 1, smallfontheight*2, 0);
			return;
		}
		super.paint(g, w, h);
	}
	
	private void showEditor() {
		editor.setParent(ui.getCanvas());
		editor.setVisible(true);
		editor.setMultiline(true);
		editor.setTouchEnabled(true);
		editor.setTextEditorListener(this);
		editor.setForegroundColor(AppUI.getColor(COLOR_MAINFG) | 0xFF000000);
		editor.setBackgroundColor(AppUI.getColor(COLOR_TOPBAR_BG) | 0xFF000000);
		setEditorPositions();
	}
	
	protected boolean keyPress(int i) {
		if(menu) {
			if(i == Canvas.KEY_NUM1) {
				menuSelection = 0;
			}
			if(itemOptions != null) {
				if(i == Canvas.KEY_NUM7) {
					menuSelection = itemOptions.length + menuOptions.length - 1;
				}
				if(i == -1 || i == Canvas.KEY_NUM2) {
					menuSelection--;
					if(menuSelection < 0) {
						menuSelection = itemOptions.length + menuOptions.length - 1;
					}
				}
				if(i == -2 || i == Canvas.KEY_NUM8) {
					menuSelection++;
					if(menuSelection > itemOptions.length + menuOptions.length - 1) {
						menuSelection = 0;
					}
				}
				if(i == -5 || i == Canvas.KEY_NUM5) {
					if(menuSelection >= itemOptions.length) {
						menuAction(menuSelection - itemOptions.length);
					} else {
						item.contextAction(menuSelection);
					}
					menu = false;
				}
			} else {
				if(i == Canvas.KEY_NUM7) {
					menuSelection = menuOptions.length - 1;
				}
				if(i == -1 || i == Canvas.KEY_NUM2) {
					menuSelection--;
					if(menuSelection < 0) {
						menuSelection = menuOptions.length - 1;
					}
				}
				if(i == -2 || i == Canvas.KEY_NUM8) {
					menuSelection++;
					if(menuSelection > menuOptions.length - 1) {
						menuSelection = 0;
					}
				}
				if(i == -5 || i == Canvas.KEY_NUM5) {
					menuAction(menuSelection);
					menu = false;
				}
			}
			if(i == -7) {
				menu = false;
			}
			repaint();
			return true;
		}
		if(search) {
			if(i == -6) {
				String s = searchText;
				if(s == null) s = "";
				TextBox t = new TextBox("", s, 256, TextField.ANY);
				t.setCommandListener(this);
				t.setTitle(Locale.s(CMD_Search));
				t.addCommand(textOkCmd);
				t.addCommand(cancelCmd);
				ui.display(t);
				return true;
			}
			if(i == -5) {
				if(searchText.length() == 0) return false;
				search = false;
				editorHidden = true;
				if(editor != null && editor.isVisible()) {
					editor.setFocus(false);
					editor.setVisible(false);
					editor.setParent(null);
				} else if(keyboard != null && keyboard.isVisible()) {
					keyboard.hide();
				}
				search(searchText);
				return true;
			}
			if(i == -7) {
				if(keyboard != null && keyboard.isVisible()) {
					if(keyboard.keyPressed(-7)) {
						return true;
					}
					onKeyboardCancel();
					return true;
				}
				search = false;
				return true;
			}
		}
		if(i == -6) {
			leftSoft();
			return true;
		}
		if(i == -7) {
			if(keyboard != null && keyboard.isVisible()) {
				onKeyboardCancel();
				return true;
			}
			if(this instanceof HomeScreen) {
				ui.exit();
			} else {
				back();
			}
			repaint();
			return true;
		}

		if(keyboard != null && keyboard.isVisible() && keyboard.keyPressed(i)) {
			return true;
		}
		if(super.keyPress(i)) {
			return true;
		}
		if(i == -3) {
			if(ui.currentTab > 0)
			selectTab(ui.currentTab-1);
			return true;
		}
		if(i == -4) {
			if(ui.currentTab < 1)
			selectTab(ui.currentTab+1);
			return true;
		}
		return false;
	}
	
	protected void leftSoft() {
		if(menuOptions != null) {
			item = cItem;
			int[] contextActions;
			if(item != null && (contextActions = item.contextActions()) != null) {
				itemOptions = new String[contextActions.length];
				for(int i = 0; i < contextActions.length; i++) {
					itemOptions[i] = Locale.s(contextActions[i]);
				}
				menuH = (menuOptions.length + itemOptions.length) * (mediumfontheight+8);
			}
			menu = true;
			menuSelection = 0;
		}
		repaint();
	}

	protected boolean keyRelease(int i) {
		if(menu) {
			return true;
		}
		if(keyboard != null && keyboard.isVisible() && keyboard.keyReleased(i)) {
			return true;
		}
		if(search) {
			return true;
		}
		return super.keyRelease(i);
	}
	
	protected void keyRepeat(int i) {
		if(menu) {
			return;
		}
		if(keyboard != null && keyboard.isVisible() && keyboard.keyRepeated(i)) {
			return;
		}
		if(search) {
			return;
		}
		super.keyRepeat(i);
	}
	
	protected void press(int x, int y) {
		if(y < topBarHeight || search | menu) return;
		super.press(x, y - topBarHeight);
	}
	
	protected void release(int x, int y) {
		if(y < topBarHeight || search || menu) return;
		super.release(x, y - topBarHeight);
	}
	
	protected boolean scroll(int units) {
		if(search || menu) return false;
		return super.scroll(units);
	}
	
	protected void tap(int x, int y, int time) {
		if(menu) {
			int xx = (lastW-menuW) >> 1;
			int yy = (lastH-menuH) >> 1;
			if(x < xx || y < yy || x > xx+menuW || y > yy+menuH) {
				menu = false;
				return;
			}
			menuAction((y-yy)/(mediumfontheight+8));
			menu = false;
			return;
		}
		if(itemMenu) {
			int xx = (lastW-menuW) >> 1;
			int yy = (lastH-itemMenuH) >> 1;
			if(x < xx || y < yy || x > xx+menuW || y > yy+itemMenuH) {
				itemMenu = false;
				return;
			}
			item.contextAction((y-yy)/(mediumfontheight+8));
			itemMenu = false;
			return;
		}
		if(y < topBarHeight) {
			if(time > 5 && time < 200 && x > 0 && x < lastW) {
				if(x < 48) {
					if(search) {
						editorHidden = true;
						if(editor != null && editor.isVisible()) {
							editor.setFocus(false);
							editor.setVisible(false);
							editor.setParent(null);
						} else if(keyboard != null && keyboard.isVisible()) {
							keyboard.hide();
						}
						search = false;
					} else if(!(this instanceof HomeScreen)) {
						back();
					}
				} else if(x > lastW - 48) {
					if(search) {
						if(searchText.length() == 0) return;
						search = false;
						editorHidden = true;
						if(editor != null && editor.isVisible()) {
							editor.setFocus(false);
							editor.setVisible(false);
							editor.setParent(null);
						} else if(keyboard != null && keyboard.isVisible()) {
							keyboard.hide();
						}
						search(searchText);
					} else {
						if(menuOptions != null) menu = true;
					}
				} else if(hasSearch) {
					if(x > lastW - 96 && !search) {
						if(editor != null) {
							editorHidden = false;
							showEditor();
							editor.setFocus(true);
						} else if(keyboard != null) {
							showKeyboard();
						}
						search = true;
					} else if(search) {
						if(editor != null) {
							if(editorHidden) {
								editorHidden = false;
								showEditor();
							}
							editor.setFocus(true);
						} else if(keyboard == null) {
							openSearchTextBox();
						}
					}
				}
			}
			return;
		}
		if(search) {
			if(searchSuggestions != null) {
				y -= 50;
				int i = y / 48;
				if(i < searchSuggestions.length) {
					setSearchText(searchSuggestions[i]);
				}
			}
			return;
		}
		if(y > lastH-topBarHeight && !(this instanceof VideoScreen)) {
			int f = lastW / 2;
			if(x > 0 && x < f) {
				selectTab(0);
			} else if(x > f && x < lastW) {
				selectTab(1);
			}
//			} else if(x > f*2 && x < lastW) {
//				selectTab(2);
//			}
			return;
		}
		super.tap(x, y - topBarHeight, time);
	}
	
	public static void selectTab(int tab) {
		if(ui.currentTab != tab && !ui.screenStacks[tab].empty()) {
			ui.screenStacks[ui.currentTab].push(ui.current);
			ui.currentTab = tab;
			ui.setScreen((UIScreen) ui.screenStacks[tab].pop());
		} else {
			switch(tab) {
			case 0:
				if(ui.mainScr == null) {
					ui.mainScr = new HomeScreen();
					new Thread(new RunnableTask(RunnableTask.MAIN)).start();
				}
				ui.screenStacks[ui.currentTab = tab].removeAllElements();
				ui.setScreen(ui.mainScr);
				break;
			case 1:
				if(ui.subsScr == null) {
					ui.subsScr = new SubscriptionsFeedScreen();
				}
				ui.screenStacks[ui.currentTab = tab].removeAllElements();
				ui.setScreen(ui.subsScr);
				break;
			}
		}
	}

	private void showKeyboard() {
		if(keyboard.getLength() == 0) {
			keyboard.setShifted(true);
		}
		keyboard.setTextColor(AppUI.getColor(COLOR_MAINFG));
		keyboard.setTextHintColor(AppUI.getColor(COLOR_GRAYTEXT));
		keyboard.setCaretColor(AppUI.getColor(COLOR_MAINFG));
		keyboard.setLanguages(Settings.inputLanguages);
		keyboard.setListener(this);
		keyboard.show();
	}

	protected void back() {
		busy = false;
		Loader.stop();
		ui.back(this);
	}

	protected void menuAction(int action) {
		switch(action) {
		case 0:
			ui.showSettings();
			break;
		case 1:
			ui.showOptions();
			break;
		}
	}

	protected void search(String s) {
		new Thread(new RunnableTask(s, RunnableTask.SEARCH)).start();
	}

	protected void openSearchTextBox() {
		if(Settings.keyboard == 2 || !Settings.fullScreen) {
			TextBox t = new TextBox("", "", 256, TextField.ANY);
			t.setCommandListener(this);
			t.setTitle(Locale.s(CMD_Search));
			t.addCommand(textOkCmd);
			t.addCommand(cancelCmd);
			ui.display(t);
			return;
		}
		initKeyboard();
		if(editor != null) {
			editorHidden = false;
			showEditor();
			editor.setFocus(true);
		} else if(keyboard != null) {
			showKeyboard();
		}
		search = true;
	}
	
	protected void setSearchText(String s) {
		searchText = s;
		if(editor != null) {
			editor.setContent(s);
		}
		if(keyboard != null) {
			keyboard.setText(s);
		}
	}

	public void inputAction(TextEditorInst editor, int event) {
		if((event & ACTION_CONTENT_CHANGE) > 0) {
			searchText = editor.getContent();
			if(suggestionsThread != null) suggestionsThread.schedule();
			if(searchText.endsWith("\n")) {
				searchText = searchText.trim();
				editor.setContent(searchText);
				if(searchText.length() > 0) {
					editor.setFocus(false);
					editor.setVisible(false);
					search(searchText);
				}
			}
		}
		if((event & ACTION_PAINT_REQUEST) > 0) {
			repaint();
		}
	}

	public void commandAction(Command c, Displayable d) {
		if(c == goCmd && d instanceof TextBox) {
			new Thread(new RunnableTask(((TextBox) d).getString(), RunnableTask.ID)).start();
			return;
		}
		if(c == textOkCmd && d instanceof TextBox) {
			search = false;
			search(searchText = ((TextBox) d).getString());
			return;
		}
		if(c == cancelCmd && d instanceof TextBox) {
			searchText = ((TextBox) d).getString();
			ui.display(null);
			return;
		}
		if(d instanceof Alert || d instanceof TextBox) {
			ui.display(null);
			return;
		}
		if(c == optsCmd) {
			leftSoft();
			return;
		}
		if(c == menuCmd) {
			ui.showOptions();
			return;
		}
		if(c == okCmd) {
			keyPress(-5);
			return;
		}
		if(c == exitCmd) {
			ui.exit();
			return;
		}
		if(c == backCmd) {
			keyPress(-7);
			return;
		}
	}
	
	public boolean blockScrolling() {
		return search || menu;
	}

	public boolean onKeyboardType(char c) {
		return true;
	}

	public boolean onKeyboardBackspace() {
		return true;
	}

	public void onKeyboardLanguageChanged() {
	}

	public void onKeyboardTextUpdated() {
		searchText = keyboard.getText();
		if(suggestionsThread != null) suggestionsThread.schedule();
	}

	public void onKeyboardDone() {
		search = false;
		keyboard.hide();
		search(searchText = keyboard.getText());
	}
	
	public void onKeyboardRepaintRequested() {
		repaint();
	}
	
	public void onTextBoxRepaintRequested() {
		repaint();
	}
	
	public final void onKeyboardCancel() {
		search = false;
		keyboard.hide();
	}

	public void loadSuggestions() {
		if(!Settings.searchSuggestions) return;
		try {
			JSONArray suggestions = ((JSONObject) App.invApi("search/suggestions?q=" + Util.url(searchText))).getArray("suggestions");
			searchSuggestions = new String[suggestions.size()];
			for(int i = 0; i < suggestions.size(); i++) {
				searchSuggestions[i] = Util.htmlText(suggestions.getString(i));
			}
		} catch (Exception e) {
		}
	}
	
	public void openItemMenu(UIItem item) {
		this.item = item;
		int[] contextActions;
		if(item != null && (contextActions = item.contextActions()) != null) {
			itemOptions = new String[contextActions.length];
			for(int i = 0; i < contextActions.length; i++) {
				itemOptions[i] = Locale.s(contextActions[i]);
			}
			itemMenuH = itemOptions.length * (mediumfontheight+8);
		}
		itemMenu = true;
		
	}
	
}
