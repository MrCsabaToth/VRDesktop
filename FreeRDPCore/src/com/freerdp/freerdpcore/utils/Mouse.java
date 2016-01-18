/*
   Android Mouse Input Mapping

   Copyright 2013 Thincast Technologies GmbH, Author: Martin Fleisz

   This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. 
   If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
*/

package com.freerdp.freerdpcore.utils;

public class Mouse {

	private final static int PTRFLAGS_MOVE = 0x0800;

	public static int getMoveEvent() {
		return PTRFLAGS_MOVE;
	}

}
