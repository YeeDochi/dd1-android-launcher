package com.winlator;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.winlator.container.Container;
import com.winlator.container.ContainerManager;
import com.winlator.dd1.DD1Game;
import com.winlator.dd1.DD1HomeState;
import com.winlator.xenvironment.RootFS;

import java.io.File;

public class DD1HomeFragment extends Fragment {
    private final Handler handler = new Handler();
    private View rootView;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dd1_home_fragment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        rootView = view;
        ((AppCompatActivity)getActivity()).getSupportActionBar().setTitle(R.string.dd1_home_title);
        view.findViewById(R.id.BTRuntimeProfiles).setOnClickListener(v ->
            ((MainActivity)getActivity()).showFragment(new ContainersFragment()));
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    public void onPause() {
        handler.removeCallbacksAndMessages(null);
        super.onPause();
    }

    private void refresh() {
        if (rootView == null) return;

        Activity activity = getActivity();
        File executable = DD1Game.findExecutable(activity.getFilesDir());
        ContainerManager manager = new ContainerManager(activity);
        Container container = manager.getContainers().isEmpty() ? null : manager.getContainers().get(0);
        DD1HomeState state = DD1HomeState.from(RootFS.find(activity).isValid(), executable != null, container != null);

        TextView status = rootView.findViewById(R.id.TVStatus);
        Button primary = rootView.findViewById(R.id.BTPrimaryAction);
        primary.setEnabled(state == DD1HomeState.READY || state == DD1HomeState.PROFILE_MISSING);

        switch (state) {
            case READY:
                status.setText(R.string.dd1_status_ready);
                primary.setText(R.string.dd1_play);
                primary.setOnClickListener(v -> launch(activity, container, executable));
                break;
            case PROFILE_MISSING:
                status.setText(R.string.dd1_status_profile_missing);
                primary.setText(R.string.dd1_create_profile);
                primary.setOnClickListener(v -> ((MainActivity)activity).showFragment(new ContainerDetailFragment()));
                break;
            case GAME_MISSING:
                status.setText(R.string.dd1_status_game_missing);
                primary.setText(R.string.dd1_play);
                primary.setOnClickListener(null);
                break;
            default:
                status.setText(R.string.dd1_status_runtime_preparing);
                primary.setText(R.string.dd1_play);
                primary.setOnClickListener(null);
                handler.postDelayed(this::refresh, 1000);
        }
    }

    private void launch(Activity activity, Container container, File executable) {
        File gameDir = new File(activity.getFilesDir(), "game");
        if (!container.getDrives().contains(gameDir.getPath())) {
            container.setDrives(container.getDrives()+"G:"+gameDir.getPath());
            container.saveData();
        }

        Intent intent = new Intent(activity, XServerDisplayActivity.class);
        intent.putExtra("container_id", container.id);
        intent.putExtra("exec_path", executable.getPath());
        activity.startActivity(intent);
    }
}
