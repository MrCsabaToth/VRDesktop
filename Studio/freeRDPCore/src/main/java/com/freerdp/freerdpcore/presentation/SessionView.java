/*
   Android Session view

   Copyright 2013 Thincast Technologies GmbH, Author: Martin Fleisz

   This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. 
   If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
*/

package com.freerdp.freerdpcore.presentation;

import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.content.Context;
import android.graphics.*;

import java.util.*;

import com.freerdp.freerdpcore.application.SessionState;


public class SessionView extends View
{
	private static final String TAG = "SessionView";

	private Bitmap surface;
	private Stack<Rect> invalidRegions;
	
	// helpers for scaling gesture handling
	private RectF invalidRegionF;

	//private static final String TAG = "FreeRDP.SessionView";

	private void initSessionView(Context context)
	{		
		invalidRegions = new Stack<Rect>();

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
		invalidRegionF.roundOut(invalidRegion);
		
		invalidRegions.add(invalidRegion);
	}

	public void invalidateRegion()
	{	
		invalidate(invalidRegions.pop());
	}

	public void onSurfaceChange(SessionState session)
	{
		surface = session.getSurface();

		setMinimumWidth(surface.getWidth());
		setMinimumHeight(surface.getHeight());

		requestLayout();
	}

	public void setZoom(float factor) {
		// update layout
		requestLayout();
	}	
	
	@Override
	public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		Log.v(TAG, surface.getWidth() + "x" + surface.getHeight());
		this.setMeasuredDimension((int)surface.getWidth(), (int)surface.getHeight());
	}

	@Override
	public void onDraw(Canvas canvas)
	{
		super.onDraw(canvas);

		canvas.save();
		//surface.draw(canvas);
		canvas.restore();
	}

}
