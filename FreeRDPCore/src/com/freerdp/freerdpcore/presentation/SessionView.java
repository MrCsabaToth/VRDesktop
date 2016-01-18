/*
   Android Session view

   Copyright 2013 Thincast Technologies GmbH, Author: Martin Fleisz

   This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. 
   If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
*/

package com.freerdp.freerdpcore.presentation;

import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.BitmapDrawable;

import java.util.*;

import com.freerdp.freerdpcore.application.SessionState;


public class SessionView extends View
{
	private int width;
	private int height;
	private BitmapDrawable surface;
	private Stack<Rect> invalidRegions;

	// helpers for scaling gesture handling
	private float scaleFactor = 1.0f;
	private Matrix scaleMatrix;
	private RectF invalidRegionF;

	private void initSessionView(Context context)
	{		
		invalidRegions = new Stack<Rect>();

		scaleFactor = 1.0f;
		scaleMatrix = new Matrix();
		scaleMatrix.setScale(scaleFactor, scaleFactor);
		invalidRegionF = new RectF();
	}	
	
	public SessionView(Context context) {
		super(context);
		initSessionView(context);
	}

	public SessionView(Context context, AttributeSet attrs) {
		super(context, attrs);
		initSessionView(context);
	}

	public SessionView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		initSessionView(context);
	}

	public void addInvalidRegion(Rect invalidRegion) {
		// correctly transform invalid region depending on current scaling
		invalidRegionF.set(invalidRegion);
		scaleMatrix.mapRect(invalidRegionF);
		invalidRegionF.roundOut(invalidRegion);
		
		invalidRegions.add(invalidRegion);
	}

	public void invalidateRegion()
	{	
//		invalidate(invalidRegions.pop());
		invalidRegions.pop();
	}

	public void onSurfaceChange(SessionState session)
	{
//		surface = session.getSurface();
//		Bitmap bitmap = surface.getBitmap();
//		width = bitmap.getWidth();
//		height = bitmap.getHeight();
//		surface.setBounds(0, 0, width, height);
		
		setMinimumWidth(width);
		setMinimumHeight(height);
		
		requestLayout();
	}

	@Override
	public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		Log.v("SessionView", width + "x" + height);
		this.setMeasuredDimension(width, height);
	}

	@Override
	public void onDraw(Canvas canvas)
	{
		super.onDraw(canvas);

		canvas.save();
		canvas.concat(scaleMatrix);
		//surface.draw(canvas);
		canvas.restore();
	}

	// dirty hack: we call back to our activity and call onBackPressed as this doesn't reach us when the soft keyboard is shown ...
	@Override
	public boolean dispatchKeyEventPreIme(KeyEvent event) {
		if(event.getKeyCode() == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_DOWN)
			((SessionActivity)this.getContext()).onBackPressed();
		return super.dispatchKeyEventPreIme(event);
	}

}
