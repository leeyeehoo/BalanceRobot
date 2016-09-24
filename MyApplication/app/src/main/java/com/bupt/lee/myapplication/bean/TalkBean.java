package com.bupt.lee.myapplication.bean;

//提问/回答
public class TalkBean {

	public TalkBean(String content, int imageId, boolean isAsk) {
		super();
		this.content = content;
		this.imageId = imageId;
		this.isAsk = isAsk;
	}

	public String content;
	public int imageId;
	public boolean isAsk;//是否是提问
}
