from flask import Flask, render_template, request, jsonify, send_from_directory
from html import escape
import os
import requests  # Install the requests library 

app = Flask(__name__)
@app.route('/static/<path:filename>')
def static_files(filename):
    return send_from_directory(os.path.join(os.getcwd(), 'static'), filename)

@app.route('/')
def index():
    return render_template('index.html')

@app.route('/send_message', methods=['POST'])
def send_message():
    user_message = request.form['user_message']
    bot_response = "This needs to be implemented in java."

    java_backend_url = "http://localhost:4321/ask-question"
    response = requests.post(java_backend_url, data=user_message)

    bot_response = escape(response.text) if response.ok else "Error communicating with the backend."

    return jsonify({'sender': 'bot', 'message': bot_response})
    
@app.route('/end_chat')
def end_chat():
    return render_template('feedback.html')

if __name__ == '__main__':
    app.run(debug=True)

