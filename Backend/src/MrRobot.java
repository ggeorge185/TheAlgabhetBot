import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;

public class MrRobot {

    private static final String INDEX_DIRECTORY = "index";
    private static final String FILES_DIRECTORY = "./AI_Articles";

    public static void main(String[] args) {
        try {
        	clearIndex();
            createIndex();
            search("What is chinese room experiment?");
        } catch (IOException e) {
            e.printStackTrace();
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
        StandardAnalyzer analyzer = new StandardAnalyzer();
        Path indexPath = FileSystems.getDefault().getPath(INDEX_DIRECTORY);
        Directory indexDirectory = FSDirectory.open(indexPath);

        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        IndexWriter indexWriter = new IndexWriter(indexDirectory, config);

        File filesDir = new File(FILES_DIRECTORY);
        File[] files = filesDir.listFiles();

        if (files != null) {
            for (File file : files) {

                
                String content = readFileContent(file);
                
                Document document = new Document();
                document.add(new TextField("content", content, Field.Store.YES));
                document.add(new StringField("filename", file.getName(), Field.Store.YES));
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

    private static void search(String queryString) throws IOException {
        StandardAnalyzer analyzer = new StandardAnalyzer();
        Path indexPath = FileSystems.getDefault().getPath(INDEX_DIRECTORY);
        Directory indexDirectory = FSDirectory.open(indexPath);

        IndexSearcher indexSearcher = new IndexSearcher(DirectoryReader.open(indexDirectory));

        QueryParser queryParser = new QueryParser("content", analyzer);

        try {
        	Query luceneQuery = preprocessQuery(queryString, queryParser);
            
            TopDocs topDocs = indexSearcher.search(luceneQuery, 5);

            System.out.println("Search results for query: " + queryString);
            boolean lock = false;
            String response = null;
            
            for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                Document document = indexSearcher.doc(scoreDoc.doc);
                //System.out.println("Filename: " + document.get("filename"));
                //System.out.println("Content: " + document.get("content"));
                if(!lock) {
                	response = document.get("content");
                	lock = true;
                }
            }
            
            System.out.print("Response: " + response);

        } catch (org.apache.lucene.queryparser.classic.ParseException e) {
            e.printStackTrace();
        }
    }
    
    private static Query preprocessQuery(String queryString, QueryParser queryParser) throws org.apache.lucene.queryparser.classic.ParseException {
        if (false || queryString.startsWith("What is") || queryString.startsWith("What are")) {
            
            Query definitionBoostQuery = new BoostQuery(queryParser.parse("definition:" + queryString.substring("What is".length())), 2.0f);
            
            return new BooleanQuery.Builder()
                    .add(new BooleanClause(definitionBoostQuery, BooleanClause.Occur.SHOULD))
                    .add(new BooleanClause(queryParser.parse(queryString), BooleanClause.Occur.SHOULD))
                    .build();
        } else {
            
            return queryParser.parse(queryString);
        }
    }
}
