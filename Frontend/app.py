from flask import Flask, render_template, request, jsonify, send_from_directory
import os

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

    return jsonify({'sender': 'bot', 'message': bot_response})

if __name__ == '__main__':
    app.run(debug=True)
