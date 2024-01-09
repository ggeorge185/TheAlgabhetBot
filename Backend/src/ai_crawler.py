import os
import requests
import re
import shutil

# Create a folder to save the articles
output_folder = 'AI_Articles'

# Delete the 'AI_Articles' folder if it already exists
if os.path.exists(output_folder):
    shutil.rmtree(output_folder)
    print("Existing folder has been removed")

os.makedirs(output_folder, exist_ok=True)

# Function to replace invalid characters in a filename
def sanitize_filename(filename):
    # Replace invalid characters and handle special cases
    return re.sub(r'[\/:*?"<>|]', '_', filename)
    
# Function to check if the content starts with a letter
def starts_with_letter(content):
    # Use regex to check if the content starts with a letter
    return re.match(r'^[a-zA-Z]', content) is not None

# Function to clean and save text content to a file
def save_text_to_file(article_title, subsection_title, content):
    sanitized_subsection_title = sanitize_filename(subsection_title)
    file_name = f"{article_title}_{sanitized_subsection_title}.txt"
    with open(os.path.join(output_folder, file_name), "w", encoding="utf-8") as file:
        file.write(content)
        
# Function to check if the content has fewer than 10 words
def is_content_informative(content):
    # Count the number of words in the content
    word_count = len(re.findall(r'\b\w+\b', content))
    return word_count >= 10

# Function to retrieve and save text content for a given title
def save_wikipedia_text(title):
    text_config = {
        'action': 'query',
        'format': 'json',
        'titles': title,
        'prop': 'revisions',
        'rvprop': 'content',
    }
    text_response = requests.get('https://en.wikipedia.org/w/api.php', params=text_config).json()
    text_page = next(iter(text_response['query']['pages'].values()))
    
    if 'revisions' in text_page:
        content = text_page['revisions'][0]['*']
        return content
    else:
        return None

# Specify the topic to search for (e.g., "Artificial_intelligence")
topic = "Artificial_intelligence"

# Get the list of articles about AI
titles_config = {
    'action': 'query',
    'format': 'json',
    'list': 'search',
    'srsearch': topic,
    'srlimit': 1000  # Set the number of articles to retrieve
}
titles_response = requests.get('https://en.wikipedia.org/w/api.php', params=titles_config).json()
titles = [result['title'] for result in titles_response['query']['search'] if '/pol/' not in result['title']]

# Iterate through the articles, retrieve text, and save to files
for title in titles:
    print(f"Processing article: {title}")
    
    # Retrieve and save text content
    text_content = save_wikipedia_text(title)
    
    if text_content:
        # Exclude content within <ref> tags
        text_content = re.sub(r'<ref.*?>.*?</ref>', '', text_content, flags=re.DOTALL)
        
        # Ignore everything after 'See also' subsection
        text_content = re.sub(r'==\s*See also\s*==.*', '', text_content, flags=re.DOTALL)
        
        # Remove patterns like "{{See also|...}}"
        text_content = re.sub(r'\{\{See also\|.*?\}\}', '', text_content)
        
        # Remove square brackets around phrases like "[[passive acoustics]]"
        text_content = re.sub(r'\[\[(.*?)\]\]', r'\1', text_content)
        
        # Split the text into subsections
        subsections = re.split(r'==\s*(.*?)\s*==', text_content)
        
        # Process each subsection individually
        for i in range(1, len(subsections), 2):
            subsection_title = subsections[i].strip()
            subsection_content = subsections[i+1]
            
            # Check if the content starts with a letter, if not, remove characters until a letter is encountered
            if not starts_with_letter(subsection_content):
                subsection_content = re.sub(r'^[^\w]*', '', subsection_content)
            
            # Remove content inside double curly brackets {{...}}
            subsection_content = re.sub(r'\{\{.*?\}\}', '', subsection_content, flags=re.DOTALL)
            
            # Remove CSS formatting inside curly brackets {}
            subsection_content = re.sub(r'\{.*?\}', '', subsection_content, flags=re.DOTALL)
            
            # Check if the content is informative
            if is_content_informative(subsection_content):
                # Save each subsection separately
                save_text_to_file(title, f"subsection_{i//2 + 1}_{subsection_title}", subsection_content)

