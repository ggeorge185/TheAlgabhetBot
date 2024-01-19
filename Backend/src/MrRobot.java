import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.custom.CustomAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.synonym.SynonymGraphFilterFactory;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;

import java.io.File;
import java.io.FileReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Scanner;

public class MrRobot {

    private static final String INDEX_DIRECTORY = "index";
    private static final String FILES_DIRECTORY = "./AI_Articles";
    private static int bestDocId = -1;
    private static final int PORT = 4321;

    public static void main(String[] args) {
    	
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
            server.createContext("/ask-question", new AskQuestionHandler());
            server.setExecutor(null);
            server.start();
            System.out.println("Server started on port " + PORT);
        } catch (IOException e) {
            e.printStackTrace();
        }
        
    	
    	/*
        Scanner scanner = new Scanner(System.in);
        String response = null;
        String query = null;
        while(true) {
            if (args.length < -1) {
                System.out.println("Please add the query!");
                response = "Sorry, I don't understand";
            } else {
                System.out.print("Ask a question: ");
                query = scanner.nextLine();
                query = query.replaceAll("\\byou\\b", "artificial intelligence");
                try {
                    clearIndex();
                    createIndex();
                    response = search(query);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            System.out.println(response);
            
            System.out.print("Was the answer helpful? (yes/no): ");
            String feedback = scanner.nextLine().toLowerCase();

            // Update document weighting based on user feedback
            try {
                updateDocumentWeighting(query, feedback);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        */
        

    }
    
    static class AskQuestionHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equals(exchange.getRequestMethod())) {
                String query = new String(exchange.getRequestBody().readAllBytes());
                String response = null;
                query = query.replaceAll("\\byou\\b", "artificial intelligence");
                try {
                    clearIndex();
                    createIndex();
                    response = search(query);
                    if(response == null) {
                    	response = "Sorry, I don't understand :( ";
                    } 
                    System.out.println(response);
                } catch (IOException e) {
                    e.printStackTrace();

                    
                    response = "Internal server error";
                    exchange.sendResponseHeaders(500, response.length());
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(response.getBytes(StandardCharsets.UTF_8));
                    }
                    return;
                }

                
                exchange.getResponseHeaders().set("Content-Type", "text/plain");
                exchange.sendResponseHeaders(200, 0);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes(StandardCharsets.UTF_8));
                }
            }
        }

    }
    

    

    private static void clearIndex() throws IOException {
        Path indexPath = FileSystems.getDefault().getPath(INDEX_DIRECTORY);
        File indexDir = indexPath.toFile();

        if (indexDir.exists()) {
            for (File file : indexDir.listFiles()) {
                file.delete();
            }
            indexDir.delete();
        }
    }

    private static void createIndex() throws IOException {
        Analyzer analyzer = CustomAnalyzer.builder()
                .withTokenizer("WhiteSpace")
                .addTokenFilter("lowercase")
                //.addTokenFilter("stop")
                .addTokenFilter(SynonymGraphFilterFactory.class, "synonyms", "synonyms.txt", "ignoreCase", "true")
                .addTokenFilter("porterstem")
                .build();
        Path indexPath = FileSystems.getDefault().getPath(INDEX_DIRECTORY);
        Directory indexDirectory = FSDirectory.open(indexPath);

        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        // Use BM25Similarity for scoring
        config.setSimilarity(new BM25Similarity());
        IndexWriter indexWriter = new IndexWriter(indexDirectory, config);

        File filesDir = new File(FILES_DIRECTORY);
        File[] files = filesDir.listFiles();

        if (files != null) {
            for (File file : files) {
                String content = readFileContent(file);

                Document document = new Document();
                document.add(new TextField("content", content, Field.Store.YES));
                document.add(new StringField("filename", file.getName(), Field.Store.YES));
                document.add(new StoredField("content_boost", "1.0")); // Default boost value
                indexWriter.addDocument(document);
            }
        }

        indexWriter.close();
    }

    private static String readFileContent(File file) throws IOException {
        StringBuilder content = new StringBuilder();
        try (FileReader reader = new FileReader(file)) {
            char[] buffer = new char[1024];
            int bytesRead;
            while ((bytesRead = reader.read(buffer)) != -1) {
                content.append(new String(buffer, 0, bytesRead));
            }
        }
        return content.toString();
    }

    private static String search(String queryString) throws IOException {
        String response = null;

        StandardAnalyzer analyzer = new StandardAnalyzer();
        Path indexPath = FileSystems.getDefault().getPath(INDEX_DIRECTORY);
        Directory indexDirectory = FSDirectory.open(indexPath);

        IndexSearcher indexSearcher = new IndexSearcher(DirectoryReader.open(indexDirectory));
        // Use BM25Similarity for scoring during search
        indexSearcher.setSimilarity(new BM25Similarity());

        QueryParser queryParser = new QueryParser("content", analyzer);

        try {
            String enhancedQ = enhanceQuery(queryParser.parse(queryString), queryString);
            Query q = queryParser.parse(enhancedQ);

            TopDocs topDocs = indexSearcher.search(q, 5);

            System.out.println("Search results for query: " + enhancedQ);
            float maxScore = Float.NEGATIVE_INFINITY;
            int bestDocId = -1;

            for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                if (scoreDoc.score > maxScore) {
                    bestDocId = scoreDoc.doc;
                    maxScore = scoreDoc.score;
                }
            }
            

            if (bestDocId != -1) {
                Document bestDocument = indexSearcher.doc(bestDocId);
                response = bestDocument.get("content").toString();
                //System.out.println("Best Document - Filename: " + bestDocument.get("filename"));
                //System.out.println("Best Document - Content: " + bestDocument.get("content"));
            }

        } catch (org.apache.lucene.queryparser.classic.ParseException e) {
            e.printStackTrace();
        }

        return response;
    }

    private static String enhanceQuery(Query originalQuery, String queryString) {
        
        String qTerm = queryString.replaceAll("(?i)^(What|Who) (is|are) ", "").replaceAll("[^a-zA-Z0-9\\s]", "");
        String whyTerm = queryString.replaceAll("(?i)^(Why)", "").replaceAll("[^a-zA-Z0-9\\s]", "");

        
        if (queryString.toLowerCase().contains("what is") || (queryString.toLowerCase().contains("who is"))) {
            return qTerm + " is a";

        } else if (queryString.toLowerCase().contains("what are") || (queryString.toLowerCase().contains("who are"))) {
            return qTerm + " are";

        } else if (queryString.toLowerCase().startsWith("why")) {
            return whyTerm + " because";

        }
        // No enhancement for other queries
        return queryString;
    }
    

    private static void updateDocumentWeighting(String query, String feedback) throws IOException {
        // Update document weighting based on user feedback for the best document
        if (bestDocId != -1) {
            Path indexPath = FileSystems.getDefault().getPath(INDEX_DIRECTORY);
            Directory indexDirectory = FSDirectory.open(indexPath);

            IndexSearcher indexSearcher = new IndexSearcher(DirectoryReader.open(indexDirectory));

            Document document = indexSearcher.doc(bestDocId);

            // Adjust document weighting based on user feedback
            float oldBoost = Float.parseFloat(document.get("content_boost"));
            float newBoost = feedback.equals("yes") ? oldBoost * 1.2f : oldBoost * 0.3f;

            // Update document in the index with the new boost value
            IndexWriterConfig config = new IndexWriterConfig(new StandardAnalyzer());
            config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);

            try (IndexWriter indexWriter = new IndexWriter(indexDirectory, config)) {
                Document updatedDocument = new Document();
                updatedDocument.add(new TextField("content", document.get("content"), Field.Store.YES));
                updatedDocument.add(new StringField("filename", document.get("filename"), Field.Store.YES));
                updatedDocument.add(new StoredField("content_boost", String.valueOf(newBoost))); // Store the boost value

                indexWriter.updateDocument(new Term("filename", document.get("filename")), updatedDocument);
            }
        }
    }
}
