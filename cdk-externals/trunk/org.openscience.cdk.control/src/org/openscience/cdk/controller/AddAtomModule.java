/* $Revision: 7636 $ $Author: nielsout $ $Date: 2007-01-04 18:46:10 +0100 (Thu, 04 Jan 2007) $
 *
 * Copyright (C) 2007  Niels Out <nielsout@users.sf.net>
 *
 * Contact: cdk-devel@lists.sourceforge.net
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2.1
 * of the License, or (at your option) any later version.
 * All I ask is that proper credit is given for my work, which includes
 * - but is not limited to - adding the above copyright notice to the beginning
 * of your source code files, and to any copyright notice that you may distribute
 * with programs based on this work.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 */
package org.openscience.cdk.controller;

import javax.vecmath.Point2d;

import org.openscience.cdk.config.IsotopeFactory;
import org.openscience.cdk.interfaces.IAtom;

/**
 * Adds an atom on the given location on mouseclick
 * 
 * @author Niels Out
 * @cdk.svnrev $Revision: 9162 $
 * @cdk.module control
 */
public class AddAtomModule extends ControllerModuleAdapter {

	public AddAtomModule(IChemModelRelay chemModelRelay) {
		super(chemModelRelay);
	}

	public void mouseClickedDown(Point2d worldCoord) {

		IAtom closestAtom = chemModelRelay.getClosestAtom(worldCoord);
		String atomType = 
			chemModelRelay.getController2DModel().getDrawElement();
		if (closestAtom == null) {
			chemModelRelay.addAtom(atomType, worldCoord);
		} else {
			closestAtom.setSymbol(atomType);
			// configure the atom, so that the atomic number matches the symbol
			try {
				IsotopeFactory.getInstance(
						closestAtom.getBuilder()).configure(closestAtom);
			} catch (Exception exception) {
				// logger.error("Error while configuring atom");
				// logger.debug(exception);
			}
		}
		chemModelRelay.updateView();
	}

	public void setChemModelRelay(IChemModelRelay relay) {
		this.chemModelRelay = relay;
	}

	public String getDrawModeString() {
		return IControllerModel.DrawMode.ADDATOMORCHANGEELEMENT.getName();
	}

}
