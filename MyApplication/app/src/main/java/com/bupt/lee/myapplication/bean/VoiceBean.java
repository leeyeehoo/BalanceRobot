package com.bupt.lee.myapplication.bean;

import java.util.ArrayList;

public class VoiceBean {

	public ArrayList<WS> ws;

	public class WS {
		public ArrayList<CW> cw;
	}

	public class CW {
		public String w;
	}
}
