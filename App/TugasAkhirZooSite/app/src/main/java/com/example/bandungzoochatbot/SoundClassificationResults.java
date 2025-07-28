package com.example.bandungzoochatbot;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.bandungzoochatbot.assets.DataAnimal;
import com.example.bandungzoochatbot.assets.DataAnimalSoundClassification;

import java.util.ArrayList;

public class SoundClassificationResults extends AppCompatActivity {
    private Button homeButton, backButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sound_classification_results);

        initializeViews();
        setupClickListeners();

        // Get data from intent (from API response)
        String animalName = getIntent().getStringExtra("animal_name");
        String animalDescription = getIntent().getStringExtra("animal_description");
        String animalFact = getIntent().getStringExtra("animal_fact");

        // Find corresponding animal data for image resource
        DataAnimal selectedAnimal = findAnimalByName(animalName);

        // Initialize views
        ImageView animalImage = findViewById(R.id.animalImageView);
        TextView animalNameView = findViewById(R.id.animalTypeTextView);
        TextView animalDesc = findViewById(R.id.animalDescriptionTextView);
        TextView animalFactView = findViewById(R.id.animalFactTextView);

        if (selectedAnimal != null) {
            // Use image from local resources
            animalImage.setImageResource(selectedAnimal.getImageResId());
            animalNameView.setText(animalName);
            // Use description and fact from API response
            animalDesc.setText(animalDescription != null ? animalDescription : selectedAnimal.getDescription());
            animalFactView.setText(animalFact != null ? animalFact : selectedAnimal.getUniqueFact());
        } else {
            // Handle case if animal is not found in local data
            animalNameView.setText(animalName != null ? animalName : "Hewan tidak ditemukan");
            animalDesc.setText(animalDescription != null ? animalDescription : "Deskripsi tidak tersedia");
            animalFactView.setText(animalFact != null ? animalFact : "Fakta unik tidak tersedia");
            animalImage.setImageResource(R.drawable.default_image);
        }
    }

    private void initializeViews() {
        homeButton = findViewById(R.id.backHome);
        backButton = findViewById(R.id.backClassify);
    }

    private void setupClickListeners() {
        homeButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, Home.class);
            startActivity(intent);
        });

        backButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, AnimalSoundClassifier.class);
            startActivity(intent);
        });
    }

    private DataAnimal findAnimalByName(String name) {
        if (name == null) return null;

        ArrayList<DataAnimal> animals = DataAnimalSoundClassification.getAnimalList();
        for (DataAnimal animal : animals) {
            // Case insensitive comparison and handle potential variations
            if (animal.getName().equalsIgnoreCase(name.trim())) {
                return animal;
            }
        }

        // Try to match with common variations
        String lowerName = name.toLowerCase().trim();
        for (DataAnimal animal : animals) {
            String animalNameLower = animal.getName().toLowerCase();
            if (animalNameLower.contains(lowerName) || lowerName.contains(animalNameLower)) {
                return animal;
            }
        }

        return null;
    }
}