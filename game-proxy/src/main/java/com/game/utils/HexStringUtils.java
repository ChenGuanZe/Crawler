package com.game.utils;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

public class HexStringUtils {

	    
	    public static final String HEX_CHAR = "0123456789ABCDEF";
	    
	    /**16进制字符串转换为字节数组
	     * @param hexString
	     * @return
	     */
		public static final byte[] hexStringToBytes(String hexString) {
			if (hexString != null && !hexString.isEmpty()) {
				String[] hexStringArray = hexString.split(" ");
				ByteArrayOutputStream baos = new ByteArrayOutputStream();

				for (String part : hexStringArray) {
					byte[] result = hexStringToBytes0(part);
					baos.write(result, 0, result.length);
				}

				return baos.toByteArray();
			} else {
				return new byte[0];
			}
		}
	    
	    public static String bytesToHexString(byte[] bArr) {
	        StringBuffer sb = new StringBuffer(bArr.length);
	        String sTmp;

	        for (int i = 0; i < bArr.length; i++) {
	            sTmp = Integer.toHexString(0xFF & bArr[i]);
	            if (sTmp.length() < 2)
	                sb.append(0);
	            sb.append(sTmp.toUpperCase());
	        }

	        return sb.toString();
	    }
	   

	    private static final byte[] hexStringToBytes0(String hexString) {
	        if (hexString.isEmpty()) 
	        {
	            throw new NullPointerException("hexString is null");
	        }
	        
	        
	        hexString = hexString.toUpperCase();
	        int length = hexString.length() / 2;
	        char[] hexChars = hexString.toCharArray();
	        byte[] d = new byte[length];
	        for (int i = 0; i < length; i++) {
	            int pos = i * 2;
	            d[i] = (byte) (charToByte(hexChars[pos]) << 4 | charToByte(hexChars[pos + 1]));
	            
	        }
	        return d;
	    }
	    private static final byte charToByte(char c) {
	        return (byte) HEX_CHAR.indexOf(c);
	    }
	    
	    
}
