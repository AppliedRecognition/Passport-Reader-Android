/*
 * aJMRTD - An Android Client for JMRTD, a Java API for accessing machine readable travel documents.
 *
 * Max Guenther, max.math.guenther@googlemail.com
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA
 *
 */



package com.appliedrec.mrtdreader;

import java.security.Provider;


public class SecurityProvider extends Provider {

    protected SecurityProvider(String name, double version, String info) {
        super(name, version, info);

        put("Mac.ISO9797ALG3MAC", "noconflict.org.bouncycastle.jce.provider.JCEMac$DES9797Alg3");
//        put("Alg.Alias.Mac.ISO9797ALG3", "ISO9797ALG3MAC");

        put("Mac.ISO9797ALG3WITHISO7816-4PADDING", "noconflict.org.bouncycastle.jce.provider.JCEMac$DES9797Alg3with7816d4");
//        put("Alg.Alias.Mac.ISO9797ALG3MACWITHISO7816-4PADDING", "ISO9797ALG3WITHISO7816-4PADDING");

        put("Signature.SHA1withRSA/ISO9796-2", "noconflict.org.bouncycastle.jce.provider.JDKISOSignature$SHA1WithRSAEncryption");
    }

}