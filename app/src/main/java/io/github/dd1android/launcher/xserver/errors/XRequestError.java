package io.github.dd1android.launcher.xserver.errors;

import static io.github.dd1android.launcher.xserver.XClientRequestHandler.RESPONSE_CODE_ERROR;

import io.github.dd1android.launcher.xconnector.XOutputStream;
import io.github.dd1android.launcher.xconnector.XStreamLock;
import io.github.dd1android.launcher.xserver.XClient;

import java.io.IOException;

public class XRequestError extends Exception  {
    private final byte code;
    private final int data;

    public XRequestError(int code, int data) {
        this.code = (byte)code;
        this.data = data;
    }

    public byte getCode() {
        return code;
    }

    public int getData() {
        return data;
    }

    public void sendError(XClient client, byte opcode) throws IOException {
        XOutputStream outputStream = client.getOutputStream();
        if (outputStream == null) return;   // client disconnected — nothing to send to
        try (XStreamLock lock = outputStream.lock()) {
            outputStream.writeByte(RESPONSE_CODE_ERROR);
            outputStream.writeByte(code);
            outputStream.writeShort(client.getSequenceNumber());
            outputStream.writeInt(data);
            outputStream.writeShort(client.getRequestData());
            outputStream.writeByte(opcode);
            outputStream.writePad(21);
        }
    }
}
