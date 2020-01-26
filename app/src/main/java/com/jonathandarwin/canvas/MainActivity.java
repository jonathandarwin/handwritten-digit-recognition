package com.jonathandarwin.canvas;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.jonathandarwin.canvas.databinding.MainActivityBinding;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;

import java.util.Random;

public class MainActivity extends AppCompatActivity
            implements View.OnClickListener{

    MainActivityBinding binding;
    DigitClassifier classifier = new DigitClassifier(this);
    //LetterClassifier classifier = new LetterClassifier(this);

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.main_activity);
        binding.setText(getRandomText());

        binding.btnGenerate.setOnClickListener(this);
        binding.btnSubmit.setOnClickListener(this);
        binding.btnClear.setOnClickListener(this);

        classifier.initialize();
    }

    private String getRandomText(){
        String text = "0123456789";
        //String text = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        String newQuest;
        Random random = new Random();
        try{
            do{
                int idx = random.nextInt(text.length());
                newQuest = Character.toString(text.charAt(idx));
            }while(newQuest.compareTo(binding.getText()) == 0);
        }catch(Exception e){
            newQuest = "0";
            //newQuest = "A";
        }
        return newQuest;
    }

    private boolean isBitmapNotNull(Bitmap b){
        Bitmap shell = Bitmap.createBitmap(b.getWidth(), b.getHeight(), b.getConfig());
        if (b.sameAs(shell)) {
            return false;
        }
        return true;
    }

    @Override
    public void onClick(View v) {
        if(v.equals(binding.btnGenerate)){
            binding.setText(getRandomText());
        } else if (v.equals(binding.btnSubmit)) {
            Bitmap bitmap = binding.canvas.getBitmap();
            if(isBitmapNotNull(bitmap)) {
                if (classifier.isInitialized()) {
                    binding.imageResult.setImageBitmap(bitmap);
                    classifier.classifyAsync(bitmap).addOnSuccessListener(new OnSuccessListener<String>() {
                        @Override
                        public void onSuccess(String s) {
                            if (s.compareTo(binding.getText()) == 0) {
                                final Toast resultToast = Toast.makeText(getApplicationContext(), "Correct!", Toast.LENGTH_SHORT);
                                resultToast.show();
                                binding.setText(getRandomText());
                                binding.canvas.clearCanvas();
                            } else {
                                final Toast resultToast = Toast.makeText(getApplicationContext(), "Incorrect!", Toast.LENGTH_SHORT);
                                resultToast.show();
                            }
                        }
                    }).addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            final Toast resultToast = Toast.makeText(getApplicationContext(), "Error Classifying Drawing.", Toast.LENGTH_SHORT);
                            resultToast.show();
                        }
                    });
                }
            }else{
                final Toast blankBitmapToast = Toast.makeText(getApplicationContext(), "You haven't draw anything!", Toast.LENGTH_SHORT);
                blankBitmapToast.show();
            }
        } else if (v.equals(binding.btnClear)){
            binding.canvas.clearCanvas();
        }
    }
}
