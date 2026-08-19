package io.github.dd1android.launcher.xserver.extensions;

import static io.github.dd1android.launcher.xserver.XClientRequestHandler.RESPONSE_CODE_SUCCESS;

import io.github.dd1android.launcher.xconnector.XInputStream;
import io.github.dd1android.launcher.xconnector.XOutputStream;
import io.github.dd1android.launcher.xconnector.XStreamLock;
import io.github.dd1android.launcher.xserver.XClient;
import io.github.dd1android.launcher.xserver.XServer;

import java.io.IOException;

public class BigReqExtension extends Extension {
    private static final int MAX_REQUEST_LENGTH = 4194303;

    public BigReqExtension(XServer xServer, byte majorOpcode) {
        super(xServer, majorOpcode);
    }

    @Override
    public String getName() {
        return "BIG-REQUESTS";
    }

    @Override
    public void handleRequest(XClient client, XInputStream inputStream, XOutputStream outputStream) throws IOException {
        try (XStreamLock lock = outputStream.lock()) {
            outputStream.writeByte(RESPONSE_CODE_SUCCESS);
            outputStream.writeByte((byte)0);
            outputStream.writeShort(client.getSequenceNumber());
            outputStream.writeInt(0);
            outputStream.writeInt(MAX_REQUEST_LENGTH);
            outputStream.writePad(20);
        }
    }
}
