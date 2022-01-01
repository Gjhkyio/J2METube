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
import cc.nnproject.json.JSONObject;

public class InvidiousException extends RuntimeException {

	private JSONObject json;
	private String url;
	private String msg2;

	public InvidiousException(JSONObject j) {
		super(j.getNullableString("error"));
		json = j;
	}

	public InvidiousException(JSONObject j, String msg, String url, String msg2) {
		super(msg);
		json = j;
		this.url = url;
		this.msg2 = msg2;
	}
	
	public JSONObject getJSON() {
		return json;
	}
	
	public String toString() {
		return "API error: " + getMessage();
	}
	
	public String toErrMsg() {
		boolean j = json != null;
		boolean bt = j && json.has("backtrace");
		boolean u = url != null;
		boolean m2 = msg2 != null;
		return  (!bt && j ? "Raw json: " + json.build() : "") + (u ? " \nAPI request: " + url : "") + (m2 ? " \n" + msg2 : "") + (bt ? " \nBacktrace: " + json.getString("backtrace") : "");
	}
	
	public String getUrl() {
		return url;
	}

}
