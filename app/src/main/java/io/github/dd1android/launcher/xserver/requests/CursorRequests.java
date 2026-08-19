package io.github.dd1android.launcher.xserver.requests;

import io.github.dd1android.launcher.xconnector.XInputStream;
import io.github.dd1android.launcher.xconnector.XOutputStream;
import io.github.dd1android.launcher.xconnector.XStreamLock;
import io.github.dd1android.launcher.xserver.Cursor;
import io.github.dd1android.launcher.xserver.Pixmap;
import io.github.dd1android.launcher.xserver.XClient;
import io.github.dd1android.launcher.xserver.errors.BadIdChoice;
import io.github.dd1android.launcher.xserver.errors.BadMatch;
import io.github.dd1android.launcher.xserver.errors.BadPixmap;
import io.github.dd1android.launcher.xserver.errors.XRequestError;

import java.io.IOException;

public abstract class CursorRequests {
    public static void createCursor(XClient client, XInputStream inputStream, XOutputStream outputStream) throws XRequestError {
        int cursorId = inputStream.readInt();
        int sourcePixmapId = inputStream.readInt();
        int maskPixmapId = inputStream.readInt();

        if (!client.isValidResourceId(cursorId)) throw new BadIdChoice(cursorId);

        Pixmap sourcePixmap = client.xServer.pixmapManager.getPixmap(sourcePixmapId);
        if (sourcePixmap == null) throw new BadPixmap(sourcePixmapId);

        Pixmap maskPixmap = client.xServer.pixmapManager.getPixmap(maskPixmapId);
        if (maskPixmap != null && (
            maskPixmap.drawable.visual.depth != 1 ||
            maskPixmap.drawable.width != sourcePixmap.drawable.width ||
            maskPixmap.drawable.height != sourcePixmap.drawable.height)) {
            throw new BadMatch();
        }

        byte foreRed = (byte)inputStream.readShort();
        byte foreGreen = (byte)inputStream.readShort();
        byte foreBlue = (byte)inputStream.readShort();
        byte backRed = (byte)inputStream.readShort();
        byte backGreen = (byte)inputStream.readShort();
        byte backBlue = (byte)inputStream.readShort();
        short x = inputStream.readShort();
        short y = inputStream.readShort();

        Cursor cursor = client.xServer.cursorManager.createCursor(cursorId, x, y, sourcePixmap, maskPixmap);
        if (cursor == null) throw new BadIdChoice(cursorId);
        client.xServer.cursorManager.recolorCursor(cursor, foreRed, foreGreen, foreBlue, backRed, backGreen, backBlue);
        client.registerAsOwnerOfResource(cursor);
    }

    public static void freeCursor(XClient client, XInputStream inputStream, XOutputStream outputStream) throws XRequestError {
        client.xServer.cursorManager.freeCursor(inputStream.readInt());
    }

    public static void getPointerMapping(XClient client, XInputStream inputStream, XOutputStream outputStream) throws IOException, XRequestError {
        try (XStreamLock lock = outputStream.lock()) {
            final byte[] buttonsMap = {1, 2, 3};
            byte length = (byte)buttonsMap.length;

            outputStream.writeByte((byte) 1);
            outputStream.writeByte(length);
            outputStream.writeShort(client.getSequenceNumber());
            outputStream.writeInt((length + 3) / 4);
            outputStream.writePad(24);
            outputStream.write(buttonsMap);
            outputStream.writePad(-length & 3);
        }
    }
}
