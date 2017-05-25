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

import acmi.l2.clientmod.io.*;
import acmi.l2.clientmod.unreal.UnrealPackageContext;
import acmi.l2.clientmod.unreal.core.Object;
import acmi.l2.clientmod.unreal.engine.StaticMesh;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;

public class Main {
    private static int version = 118;
    private static int license = 0;

    public static void processFileOrFolder(File file) {
        if (file.isDirectory()) {
            for (File sub : Optional.ofNullable(file.listFiles()).orElse(new File[0]))
                processFileOrFolder(sub);
        } else {
            if (file.length() < 4)
                return;

            try (DataInputStream dis = new DataInputStream(new FileInputStream(file))) {
                int i = dis.readInt();
                if (i != 0xC1832A9E && i != 0x4C006900)
                    return;
            } catch (Exception e) {
                System.err.println("Couldn't open " + file + ": " + e.getMessage());
                return;
            }

            try (UnrealPackage up = new UnrealPackage(file, false)) {
                if (up.importReferenceByName("Engine.StaticMesh", c -> c.equalsIgnoreCase("Core.Class")) == 0)
                    return;
            } catch (Exception e) {
                System.err.println("Invalid unreal package " + file + ": " + e.getMessage());
                return;
            }

            processFile(file);
        }
    }

    private static void processFile(File file) {
        try (UnrealPackage up = new UnrealPackage(file, false)) {
            Map<UnrealPackage.ExportEntry, StaticMesh> map = new LinkedHashMap<>();
            Map<UnrealPackage.ExportEntry, byte[]> propertiesMap = new HashMap<>();
            List<UnrealPackage.ExportEntry> toRemove = new ArrayList<>();

            SerializerFactory<UnrealPackageContext> serializer = new ReflectionSerializerFactory<>();
            UnrealPackageContext context = new UnrealPackageContext(up);
            for (UnrealPackage.ExportEntry entry : up.getExportTable()) {
                if (entry.getFullClassName().equalsIgnoreCase("Engine.StaticMesh")) {
                    StaticMesh sm = new StaticMesh();
                    ByteBuffer buffer = ByteBuffer.wrap(entry.getObjectRawData()).order(ByteOrder.LITTLE_ENDIAN);
                    DataInput di = DataInput.dataInput(buffer, up.getFile().getCharset());
                    ObjectInput<UnrealPackageContext> oi = ObjectInput.objectInput(di, serializer, context);
                    PropertiesUtil.iterateProperties(oi, up);
                    byte[] properties = new byte[buffer.position()];
                    System.arraycopy(buffer.array(), 0, properties, 0, properties.length);
                    propertiesMap.put(entry, properties);
                    oi.getSerializerFactory().forClass(Object.Box.class).readObject(sm.boundingBox = new Object.Box(), oi);
                    oi.getSerializerFactory().forClass(Object.Sphere.class).readObject(sm.boundingSphere = new Object.Sphere(), oi);
                    sm.readStaticMesh(oi);
                    map.put(entry, sm);
                    if (buffer.position() != buffer.limit())
                        throw new IllegalStateException(entry.getObjectFullName() + " " + up.getVersion() + "_" + up.getLicense() + " " + buffer.position() + "/" + buffer.limit());
                } else if (entry.getFullClassName().equalsIgnoreCase("Engine.Model") ||
                        entry.getFullClassName().equalsIgnoreCase("Engine.Polys")) {
                    toRemove.add(entry);
                }
            }

            up.setVersion(version);
            up.setLicense(license);

            for (Map.Entry<UnrealPackage.ExportEntry, StaticMesh> entry : map.entrySet()) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                DataOutput dataOutput = DataOutput.dataOutput(baos, up.getFile().getCharset(), entry.getKey().getOffset());
                ObjectOutput<UnrealPackageContext> oo = ObjectOutput.objectOutput(dataOutput, serializer, context);
                oo.writeBytes(propertiesMap.get(entry.getKey()));
                oo.getSerializerFactory().forClass(Object.Box.class).writeObject(entry.getValue().boundingBox, oo);
                oo.getSerializerFactory().forClass(Object.Sphere.class).writeObject(entry.getValue().boundingSphere, oo);
                entry.getValue().writeStaticMesh(oo);
                entry.getKey().setObjectRawData(baos.toByteArray());
            }

            for (UnrealPackage.ExportEntry entry : toRemove) {
                System.out.println("\tRemove: " + entry);
                up.removeExport(entry.getIndex());
            }
            up.updateExportTable(exports -> {
            });

            System.out.println("Patched: " + file);
        } catch (Throwable e) {
            System.err.println(file);
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        if (args.length != 3) {
            System.out.println("USAGE: java -jar smconv.jar C:\\Lineage2\\StaticMeshes 118 0");
            System.out.println("NOTE: it doesn't decrypt files, you have to do it manually");
            System.exit(1);
        }
        version = Integer.parseInt(args[1]);
        license = Integer.parseInt(args[2]);
        processFileOrFolder(new File(args[0]).getAbsoluteFile());
    }
}
