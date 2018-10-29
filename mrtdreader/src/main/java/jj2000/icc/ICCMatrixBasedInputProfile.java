/*****************************************************************************
 *
 * $Id: ICCMatrixBasedInputProfile.java 166 2012-01-11 23:48:05Z mroland $
 *
 * Copyright Eastman Kodak Company, 343 State Street, Rochester, NY 14650
 * $Date $
 *****************************************************************************/

package jj2000.icc;

import jj2000.colorspace.ColorSpace;
import jj2000.colorspace.ColorSpaceException;

/**
 * This class enables an application to construct an 3 component ICCProfile
 * 
 * @version	1.0
 * @author	Bruce A. Kern
 */

public class ICCMatrixBasedInputProfile extends ICCProfile {
     
    /**
     * Factory method to create ICCMatrixBasedInputProfile based on a
     * suppled profile file.
     *   @param f contains a disk based ICCProfile.
     * @return the ICCMatrixBasedInputProfile
     * @exception ICCProfileInvalidException
     * @exception ColorSpaceException
     */
    public static ICCMatrixBasedInputProfile createInstance (ColorSpace csm) 
        throws ColorSpaceException, ICCProfileInvalidException {
        return new ICCMatrixBasedInputProfile (csm); }

    /**
     * Construct an ICCMatrixBasedInputProfile based on a
     * suppled profile file.
     *   @param f contains a disk based ICCProfile.
     * @exception ColorSpaceException
     * @exception ICCProfileInvalidException
     */
    protected ICCMatrixBasedInputProfile (ColorSpace csm)
        throws ColorSpaceException, ICCProfileInvalidException {
            super (csm); }
    
    /* end class ICCMatrixBasedInputProfile */ }
















