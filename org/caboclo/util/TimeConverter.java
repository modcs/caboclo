/*
**The MIT License (MIT)
**Copyright (c) <2014> <CIn-UFPE>
** 
**Permission is hereby granted, free of charge, to any person obtaining a copy
**of this software and associated documentation files (the "Software"), to deal
**in the Software without restriction, including without limitation the rights
**to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
**copies of the Software, and to permit persons to whom the Software is
**furnished to do so, subject to the following conditions:
** 
**The above copyright notice and this permission notice shall be included in
**all copies or substantial portions of the Software.
** 
**THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
**IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
**FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
**AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
**LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
**OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
**THE SOFTWARE.
*/


package org.caboclo.util;


public class TimeConverter {
    
    /**
     * A method that receives a long ms as parameter and returns a String converted 
     * in time (HH:mm:ss:ms)
     * @param ms
     * @return 
     */
    public static String converteToTime(long ms) {
        int s, m, h;
        String tempoPronto = "";

        s = (int) ms / 1000;
        ms -= s * 1000;
        m = s / 60;
        s -= m * 60;
        h = m / 60;
        m -= h * 60;

        tempoPronto += (h < 10) ? "0" + h : h;
        tempoPronto += (m < 10) ? ":0" + m : ":" + m;
        tempoPronto += (s < 10) ? ":0" + s : ":" + s;
        tempoPronto += (ms < 10) ? ".00" + ms : (ms < 100) ? ".0" + ms : "." + ms;

        return tempoPronto;
    }    
}
