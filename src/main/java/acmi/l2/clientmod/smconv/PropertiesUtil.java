/*
 * Copyright (c) 2017 acmi
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package acmi.l2.clientmod.smconv;

import acmi.l2.clientmod.io.DataInput;
import acmi.l2.clientmod.io.UnrealPackage;

import java.nio.BufferUnderflowException;

public class PropertiesUtil {

    public static void iterateProperties(DataInput input, UnrealPackage up) throws BufferUnderflowException {
        String name;
        while (!"None".equals(name = up.nameReference(input.readCompactInt()))) {
            int info = input.readUnsignedByte();
            Type type = Type.values()[info & 15];
            int size = (info & 112) >> 4;
            boolean array = (info & 128) == 128;
            String struct = type == Type.STRUCT ? up.nameReference(input.readCompactInt()) : null;
            size = getSize(size, input);
            int arrayIndex = array && type != Type.BOOL ? input.readCompactInt() : 0;
            input.skip(size);
        }
    }

    public static int getSize(int size, DataInput input) throws BufferUnderflowException {
        switch (size) {
            case 0:
                return 1;
            case 1:
                return 2;
            case 2:
                return 4;
            case 3:
                return 12;
            case 4:
                return 16;
            case 5:
                return input.readUnsignedByte();
            case 6:
                return input.readUnsignedShort();
            case 7:
                return input.readInt();
        }
        throw new RuntimeException("invalid size " + size);
    }

    public enum Type {
        NONE,
        BYTE,
        INT,
        BOOL,
        FLOAT,
        OBJECT,
        NAME,
        _DELEGATE,
        _CLASS,
        ARRAY,
        STRUCT,
        _VECTOR,
        _ROTATOR,
        STR,
        _MAP,
        _FIXED_ARRAY
    }
}