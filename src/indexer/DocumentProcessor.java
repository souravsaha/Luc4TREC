
package indexer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import static common.trec.DocField.FIELD_BOW;
import static common.trec.DocField.FIELD_FULL_BOW;
import static common.trec.DocField.FIELD_ID;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import static common.CommonMethods.analyzeText;
import java.util.Arrays;
import java.util.List;
import org.apache.lucene.document.LongField;
import static common.wapo.DocField.WAPO_URL;
import static common.wapo.DocField.WAPO_TITLE;
import static common.wapo.DocField.WAPO_AUTHOR;
import static common.wapo.DocField.WAPO_DATE;
import static common.wapo.DocField.WAPO_CONTENT;
import static common.wapo.DocField.WAPO_CATEGORY;

/**
 * Processes (removes stopwords and stems; also, removes URLs, tags etc.) the content of each document
 * @author dwaipayan
 */
public class DocumentProcessor {
    
    String      toStore;            // YES / NO; to be read from prop file; default is 'NO'
    String      storeTermVector;    // NO / YES / WITH_POSITIONS / WITH_OFFSETS / WITH_POSITIONS_OFFSETS; to be read from prop file; default - YES
    String      dumpPath;           // path to dump the analyzed content 

    protected BufferedReader docReader; // document reader
    protected boolean at_eof = false;   // whether at the end of file or not
    protected Analyzer    analyzer;

    StringBuffer rawDocSb;      // one entire document from the marked-up file, as-it-is.

    String      doc_no;         // trec-docid
    String      cleanContent;   // clean content of a document
    String      metaContent;    // meta content of a document

    // +++ For replacing characters- ':','_'
    Map<String, String> replacements = new HashMap<String, String>() {{
        put(":", " ");
        put("_", " ");
    }};
    // create the pattern joining the keys with '|'
    String regExp = ":|_";
    Pattern p = Pattern.compile(regExp);
    // --- For replacing characters- ':','_'

    public static final List<String> WAPO_CONTENT_TYPE_TAG = Arrays.asList("kicker", "sanitized_html", "title");

    /**
     * Removes the HTML tags from 'str' and returns the resultant string
     * @param str
     * @return 
     */
    public String removeHTMLTags(String str) {

        String tagPatternStr = "<[^>\\n]*[>\\n]";
        Pattern tagPattern = Pattern.compile(tagPatternStr);

        Matcher m = tagPattern.matcher(str);

	while(m.find())
            metaContent += (m.group(0)+" ");

        return m.replaceAll(" ");
    }

    /**
     * Removes URLs from 'str' and returns the resultant string
     * @param str
     * @return 
     */
    public String removeURL(String str) {

        String urlPatternStr = "\\b((https?|ftp|file)://|www)[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]";
        Pattern urlPattern = Pattern.compile(urlPatternStr);

        Matcher m = urlPattern.matcher(str);

        while(m.find())
            metaContent += (m.group(0)+" ");

        return m.replaceAll(" ");
    }

    /**
     * Removes strings with digits and punctuation on them.
     * @param tokens
     * @return 
     */
    public String refineSpecialChars(String tokens) {

        if(tokens!=null)
            tokens = tokens.replaceAll("\\p{Punct}+", " ");

        return tokens;
    }

    public String filterWebText(String text) {

        String urlPatternStr = "\\b((https?|ftp|file)://|www)[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]";
        String tagPatternStr = "<[^>\\n]*[>\\n]";

        Pattern webFilter = Pattern.compile(tagPatternStr+"|"+urlPatternStr);
        Matcher matcher = webFilter.matcher(text);

        return matcher.replaceAll(" ");
    }

    /**
     * Analyzes the text and sets the fields of Lucene Document
     * @return Lucene document to be indexed
     * @throws IOException 
     */
    protected Document processDocument() throws IOException {

        Document doc = new Document();

        // Field: FIELD_ID
        // The TREC-ID of the document.
        doc.add(new StringField(FIELD_ID, doc_no, Field.Store.YES));

        String fullText = rawDocSb.toString();
        fullText = refineSpecialChars(fullText);
        String fullContent = analyzeText(analyzer, fullText, FIELD_FULL_BOW).toString();
        // Field: FIELD_FULL_BOW
        // Full analyzed content (with tags, urls). 
        // Will be used for baseline retrieval.
        doc.add(new Field(FIELD_FULL_BOW, fullContent, 
            Field.Store.valueOf(toStore), Field.Index.ANALYZED, Field.TermVector.valueOf(storeTermVector)));

        // if to index the clean content:
        {
            cleanContent = rawDocSb.toString();

            cleanContent = filterWebText(cleanContent);

            cleanContent = analyzeText(analyzer, cleanContent, FIELD_BOW).toString();

            // Field: FIELD_BOW
            // Clean analyzed content (without tag, urls).
            // Will be used for Relevance Feedback.
            doc.add(new Field(FIELD_BOW, cleanContent, 
                Field.Store.valueOf(toStore), Field.Index.ANALYZED, Field.TermVector.valueOf(storeTermVector)));

            // TODO: Uncomment, to index the meta content that are removed due to noise removal
            /*
            // Field: FIELD_META
            // the noises that were removed from full to get the clean content
            String analyzedMetaText = analyzeText(analyzer, metaContent, FIELD_META).toString();
            doc.add(new Field(FIELD_META, analyzedMetaText, 
                Field.Store.valueOf(toStore), Field.Index.ANALYZED, Field.TermVector.valueOf(storeTermVector)));
            //*/
        }

        return doc;
    } // ends processDocument()

    /**
     * Analyzes the text and sets the fields of Lucene Document
     * @return Lucene document to be indexed
     * @throws IOException 
     */
    protected Document processNewsDocument() throws IOException {

        Document doc = new Document();

        // Field: FIELD_ID
        // The TREC-ID of the document.
        doc.add(new StringField(FIELD_ID, doc_no, Field.Store.YES));

        cleanContent = rawDocSb.toString();

        cleanContent = filterWebText(cleanContent);
        cleanContent = refineSpecialChars(cleanContent);

        // <uncomment>
//        /*
        // to analyze the text manually, uncomment following line;
        // needed in case we want to store the analyzed text 
        cleanContent = analyzeText(analyzer, cleanContent, FIELD_BOW).toString();
//        */
        // </uncomment>

        // Field: FIELD_BOW
        // Clean analyzed content (without tag, urls).
        doc.add(new Field(FIELD_BOW, cleanContent, 
            Field.Store.valueOf(toStore), Field.Index.ANALYZED, Field.TermVector.valueOf(storeTermVector)));

        return doc;
    } // ends processDocument()

    /**
     * Returns only the tag, meta-content of a document.
     * @param htmlText
     * @return Tag and meta-content
     */
    public static String getTagMetaContent(String htmlText) {
        String meta;
//        Document doc = Jsoup.parse(htmlText, "", Parser.xmlParser());
        org.jsoup.nodes.Document jsoupDoc = org.jsoup.Jsoup.parse(htmlText, "", org.jsoup.parser.Parser.xmlParser());

        for (org.jsoup.nodes.Element el : jsoupDoc.select("*")){
            if (!el.ownText().isEmpty()){
                for (org.jsoup.nodes.TextNode node : el.textNodes())
                    node.remove();
            }
        }

        System.out.println(jsoupDoc);
        meta = jsoupDoc.text();
        return meta;
    }

    /**
     * Analyze the documents using JSoup
     * Making two fields:
     *  1. FIELD_ID  : The TREC-ID of the document.
     *  2. FIELD_BOW : Clean analyzed content (without tag, urls).
     * @return
     * @throws IOException 
     */
    protected Document processDocumentUsingJSoup() throws IOException {

        Document doc = new Document();

        // Field: FIELD_ID
        // The TREC-ID of the document.
        doc.add(new StringField(FIELD_ID, doc_no, Field.Store.YES));

        String fullText = rawDocSb.toString();
        fullText = refineSpecialChars(fullText);
        String fullContent = analyzeText(analyzer, fullText, FIELD_FULL_BOW).toString();

        /*
        // Field: FIELD_FULL_BOW
        // Full analyzed content (with tags, urls). 
        // NOT INDEXING.
        doc.add(new Field(FIELD_FULL_BOW, fullContent, 
            Field.Store.valueOf(toStore), Field.Index.ANALYZED, Field.TermVector.valueOf(storeTermVector)));
        */

        cleanContent = rawDocSb.toString();
        org.jsoup.nodes.Document jsoupDoc;
        jsoupDoc = org.jsoup.Jsoup.parse(cleanContent, "UTF-8");

        // metaContent = getTagMetaContent(cleanText);
        cleanContent = jsoupDoc.text();

        cleanContent = analyzeText(analyzer, cleanContent, FIELD_BOW).toString();

        // Field: FIELD_BOW
        // Clean analyzed content (without tag, urls).
        doc.add(new Field(FIELD_BOW, cleanContent, 
            Field.Store.valueOf(toStore), Field.Index.ANALYZED, Field.TermVector.valueOf(storeTermVector)));

        return doc;
    } // ends processDocumentUsingJSoup()

    protected Document processJsonDocument(String line) throws IOException {

        Document doc = new Document();

        org.jsoup.nodes.Document jsoupDoc;

        ObjectMapper objectMapper = new ObjectMapper();

        JsonNode json = objectMapper.readTree(line);

        JsonNode doc_id = json.get("id");
        JsonNode url = json.get("article_url");
        JsonNode title = json.get("title");
        JsonNode author = json.get("author");
        JsonNode date = json.get("published_date");
        ArrayNode contents = (ArrayNode) json.get("contents");

        String doc_category = "";

        // System.out.println("DocID: " + doc_id);
        StringBuffer rawContent = new StringBuffer();
        String content_str;

        for(JsonNode content : contents) {
            if(content.get("type")!=null && WAPO_CONTENT_TYPE_TAG.contains(content.get("type").asText())) {
                content_str = content.get("content").asText();
                if(content.get("type").asText().equals("kicker"))
                    doc_category = content_str;
                rawContent.append(content_str).append("\n");
            }
        }

        jsoupDoc = org.jsoup.Jsoup.parse(rawContent.toString(), "UTF-8");

        cleanContent = jsoupDoc.text();

        // Field: FIELD_ID
        // The TREC-ID of the document.
        doc.add(new StringField(FIELD_ID, doc_id.asText(), Field.Store.YES));

        doc.add(new StringField(WAPO_URL, url.asText(), Field.Store.YES));

        doc.add(new StringField(WAPO_TITLE, title.asText(), Field.Store.YES));

        doc.add(new StringField(WAPO_AUTHOR, author.asText(), Field.Store.YES));

        doc.add(new LongField(WAPO_DATE, (long)date.asLong(), Field.Store.YES));

        doc.add(new StringField(WAPO_CATEGORY, doc_category, Field.Store.YES));

        cleanContent = filterWebText(cleanContent);
        cleanContent = refineSpecialChars(cleanContent);

        // <uncomment>
//        /*
        // to analyze the text manually, uncomment following line;
        // needed in case we want to store the analyzed text 
        cleanContent = analyzeText(analyzer, cleanContent, WAPO_CONTENT).toString();
//        */
        // </uncomment>

        doc.add(new Field(WAPO_CONTENT, cleanContent, 
            Field.Store.valueOf(toStore), Field.Index.ANALYZED, Field.TermVector.valueOf(storeTermVector)));

        return doc;
    } // ends processJsonDocument()

}
