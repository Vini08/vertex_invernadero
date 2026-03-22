package com.agrocontrol.app.ui;
import android.os.Bundle;
import android.view.*;
import androidx.annotation.*;
import androidx.fragment.app.Fragment;
import com.agrocontrol.app.R;
public class PumpFragment extends Fragment {
    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater i,@Nullable ViewGroup c,@Nullable Bundle s){
        return i.inflate(R.layout.fragment_pump,c,false);
    }
}
