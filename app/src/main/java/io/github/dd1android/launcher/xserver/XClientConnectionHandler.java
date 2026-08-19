package io.github.dd1android.launcher.xserver;

import io.github.dd1android.launcher.xconnector.ConnectedClient;
import io.github.dd1android.launcher.xconnector.ConnectionHandler;

public class XClientConnectionHandler implements ConnectionHandler {
    private final XServer xServer;

    public XClientConnectionHandler(XServer xServer) {
        this.xServer = xServer;
    }

    @Override
    public ConnectedClient newConnectedClient(long clientPtr, int fd) {
        return new XClient(clientPtr, fd, xServer);
    }

    @Override
    public void handleNewConnection(ConnectedClient client) {}

    @Override
    public void handleConnectionShutdown(ConnectedClient client) {
        ((XClient)client).freeResources();
    }
}
