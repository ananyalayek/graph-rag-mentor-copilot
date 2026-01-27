
import pandas as pd
from faker import Faker
import random

# Initialize Faker
fake = Faker()

# --- Configuration for Data Generation ---

# Possible values for different fields
SKILL_LIST = [
    "Basic Computer Literacy", "Microsoft Office", "Typing", "Communication",
    "Teamwork", "Problem Solving", "Customer Service", "Sales", "Marketing",
    "Social Media Management", "Data Entry", "Graphic Design (Beginner)",
    "Video Editing (Beginner)", "HTML/CSS (Basics)", "Python (Basics)",
    "Java (Basics)", "SQL (Basics)"
]

INTEREST_LIST = [
    "Technology", "Creative Arts", "Gaming", "Social Media", "Entrepreneurship",
    "Helping Others", "Sports", "Music", "Movies", "Reading", "Writing"
]

EDUCATION_LEVELS = [
    "10th Pass", "12th Pass", "Undergraduate (In Progress)",
    "Graduate", "Diploma Holder"
]

NUM_STUDENTS = 500

# --- Data Generation Logic ---

def create_student_record(student_id):
    """Generates a single synthetic student record."""
    name = fake.name()
    
    # Assign a random number of skills and interests
    num_skills = random.randint(2, 5)
    current_skills = random.sample(SKILL_LIST, num_skills)
    
    num_interests = random.randint(1, 3)
    interests = random.sample(INTEREST_LIST, num_interests)
    
    education_level = random.choice(EDUCATION_LEVELS)
    
    return {
        "student_id": student_id,
        "name": name,
        "current_skills": ", ".join(current_skills), # Store as comma-separated string
        "interests": ", ".join(interests),
        "education_level": education_level
    }

def generate_dataset(num_records):
    """Generates a list of student records."""
    all_students = []
    for i in range(1, num_records + 1):
        all_students.append(create_student_record(i))
    return all_students

# --- Main Execution ---

if __name__ == "__main__":
    print(f"Generating synthetic data for {NUM_STUDENTS} students...")
    
    student_data = generate_dataset(NUM_STUDENTS)
    
    # Create a pandas DataFrame
    df = pd.DataFrame(student_data)
    
    # Define the output CSV file name
    output_filename = "students.csv"
    
    # Save the DataFrame to a CSV file
    df.to_csv(output_filename, index=False)
    
    print(f"Successfully generated and saved data to '{output_filename}'.")
    print("\n--- Sample Data (First 5 Rows) ---")
    print(df.head())
    print("\n------------------------------------")
