package com.bupt.lee.myapplication.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Face;
import android.util.AttributeSet;
import android.widget.ImageView;

import com.bupt.lee.myapplication.R;
import com.bupt.lee.myapplication.camera.CameraInterface;
import com.bupt.lee.myapplication.util.Util;

public class FaceView extends ImageView {
	private Context mContext;
	private Paint mLinePaint;
	private Face[] mFaces;
	private Matrix mMatrix = new Matrix();
	private RectF mRect = new RectF();
	private Drawable mFaceIndicator = null;
	private int SetX=0;
	private int Length=0;
	public FaceView(Context context, AttributeSet attrs) {
		super(context, attrs);
		// TODO Auto-generated constructor stub
		initPaint();
		mContext = context;
		mFaceIndicator = getResources().getDrawable(R.drawable.ic_face_find_2);
	}


	public void setFaces(Face[] faces){
		this.mFaces = faces;
		invalidate();
	}
	public void clearFaces(){
		mFaces = null;
		invalidate();
	}
	

	@Override
	protected void onDraw(Canvas canvas) {
		// TODO Auto-generated method stub
		SetX=0;
		Length=0;
		if(mFaces == null || mFaces.length < 1){
			return;
		}
		boolean isMirror = false;
		int Id = CameraInterface.getInstance().getCameraId();
		if(Id == CameraInfo.CAMERA_FACING_BACK){
			isMirror = false; //����Camera����mirror
		}else if(Id == CameraInfo.CAMERA_FACING_FRONT){
			isMirror = true;  //ǰ��Camera��Ҫmirror
		}
		Util.prepareMatrix(mMatrix, isMirror, 90, getWidth(), getHeight());
		canvas.save();
		mMatrix.postRotate(0); //Matrix.postRotateĬ����˳ʱ��
		canvas.rotate(-0);   //Canvas.rotate()Ĭ������ʱ��
		for(int i = 0; i< mFaces.length; i++){
			mRect.set(mFaces[i].rect);
			mMatrix.mapRect(mRect);
			mFaceIndicator.setBounds(Math.round(mRect.left), Math.round(mRect.top),
					Math.round(mRect.right), Math.round(mRect.bottom));
			mFaceIndicator.draw(canvas);
			SetX=SetX+ (Math.round(mRect.top)+Math.round(mRect.bottom)/2);
			Length=Length+(Math.round(mRect.top)-Math.round(mRect.bottom));
//			canvas.drawRect(mRect, mLinePaint);
		}
		SetX=SetX/mFaces.length;
		Length=Length/mFaces.length;
		canvas.restore();
		super.onDraw(canvas);

	}

	public int getSetX(){
		return SetX;
	}
	public int getLength(){
		return Length;
	}
	private void initPaint(){
		mLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
//		int color = Color.rgb(0, 150, 255);
		int color = Color.rgb(98, 212, 68);
//		mLinePaint.setColor(Color.RED);
		mLinePaint.setColor(color);
		mLinePaint.setStyle(Style.STROKE);
		mLinePaint.setStrokeWidth(5f);
		mLinePaint.setAlpha(180);
	}
}
