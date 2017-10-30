import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.file.Paths;
import java.util.HashMap;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.*;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;
import org.jdom2.*;
import org.jdom2.input.SAXBuilder;

public class generateIndex {

	private static final String indexPath = "D:/IU/Search_workspace/index/";
	private static final String corpusPath = "D:/IU/Search_workspace/corpus";
	
	public static void main(String[] args) throws JDOMException {
		
		System.out.println("Create Index..");		
		try {
			Directory dir = FSDirectory.open(Paths.get(indexPath));
			Analyzer analyzer = new StandardAnalyzer();
			IndexWriterConfig iwc = new IndexWriterConfig(analyzer);
			iwc.setOpenMode(OpenMode.CREATE);
			IndexWriter writer = new IndexWriter(dir,iwc);
			File[] listFiles =new File(corpusPath).listFiles();
			//System.out.println(listFiles.length);
			//Check files are available at the given path
			if(listFiles != null) {
				//Iterate over each file
				for(File file : listFiles) {
					//Check the file extension before reading the file
					if(file.getPath().toLowerCase().endsWith(".trectext")) {
						
						//Read the file into string
						InputStream is = new FileInputStream(file.getAbsolutePath());
						BufferedReader buf = new BufferedReader(new InputStreamReader(is));
						StringBuilder sb = new StringBuilder();
						String line = buf.readLine();
						while(line != null){
							sb.append(line).append("\n");
							line = buf.readLine();
						}
						buf.close();
						//Replace all the & in the file to &amp; 
						//& is a special character in XML and XML parser throws error on encountering & in text.
						String fileDoc = sb.toString().replaceAll("&", "&amp;");
						
						//Extract all documents from the file by splitting the file.
						String[] docArray= fileDoc.split("</DOC>");
						
						//Iterate over each doc from the file and add it to index writer
						for(int i =0; i<docArray.length-1;i++) {
							
							if(docArray[i]!=null) {
								// Checking the null values in array to avoid IllegalException error
								
								//Add </DOC> back to each split, since the string returned by split() does not contain the phrase it split on.
								String doc = docArray[i].trim() + "\n</DOC>";
								
								// Call method trecParse to Parse each doc
								HashMap<String, String> result = trecParse(doc);
								
								//Add to lucene Document for indexing
								Document luceneDoc = new Document();
								if(result.get("DOCNO")!= null) {
									luceneDoc.add(new StringField("DOCNO", result.get("DOCNO"),Field.Store.YES));
								}
								if(result.get("HEAD")!= null) {
									luceneDoc.add(new TextField("HEAD", result.get("HEAD"), Field.Store.YES));
								}
								if(result.get("BYLINE")!= null) {
									luceneDoc.add(new TextField("BYLINE", result.get("BYLINE"),Field.Store.YES));
								}
								if(result.get("DATELINE")!= null) {
									luceneDoc.add(new TextField("DATELINE", result.get("DATELINE"),Field.Store.YES));
								}
								if(result.get("TEXT")!=null) {
									luceneDoc.add(new TextField("TEXT", result.get("TEXT"),Field.Store.YES));
								}
								// Add Each Lucene Document to IndexWriter
								writer.addDocument(luceneDoc);
							}
						}
					}
				}
			}	
			writer.forceMerge(1);
			writer.commit();
			writer.close();
			//Print Index Statistics
			getStats();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}	
	}

// Method to Parse Each Document
	private static HashMap<String, String> trecParse(String doc) throws JDOMException, IOException {
		//Define String variables to extract content between tags
		String docno = "";
		String head ="";
		String byline ="";
		String dateline = "";
		String text ="";
		
		//Store the tag and corresponding text to a HashMap
		HashMap<String, String> document = new HashMap<String, String>();
		
		//Using SAX Builder to Parse XML Styled Document
		SAXBuilder saxBuilder = new SAXBuilder(); 
		org.jdom2.Document xmlDoc = saxBuilder.build(new StringReader(doc));
		//Get the root element
		Element root = xmlDoc.getRootElement();
		
		// Get <DOCNO>, <BYLINE>, <HEAD>, <DATELINE>, and <TEXT> elements
		// If same tag is repeated more than once, append content to same string
		if(root.getChildren("HEAD").size() >= 1) {

			for (Element heads: root.getChildren("HEAD")) {
				head = head + " " + heads.getText();
			}
			document.put("HEAD",head.trim());
		}

		if(root.getChildren("DOCNO").size() >= 1) {
			for (Element docnos: root.getChildren("DOCNO")) {
				docno = docno+ " " +docnos.getText();
			}
			document.put("DOCNO",docno.trim());
		}
		
		if(root.getChildren("BYLINE").size() >= 1) {
			for (Element bylines: root.getChildren("BYLINE")) {
				byline = byline + " " + bylines.getText();
			}
			document.put("BYLINE",byline.trim());
		}

		if(root.getChildren("DATELINE").size() >= 1) {
			for (Element datelines: root.getChildren("DATELINE")) {
				dateline = dateline + " "+ datelines.getText();
			}
			document.put("DATELINE",dateline.trim());
		}						
	
		if(root.getChildren("TEXT").size() >= 1) {
			for (Element texts: root.getChildren("TEXT")) {
				text = text + " " + texts.getText();
			}
			document.put("TEXT",text.trim());
		}
		return document;
	}

	//Method to calculate index statistics
	private static void getStats() throws IOException {
		
		IndexReader reader = DirectoryReader.open(FSDirectory.open(Paths.get(indexPath)));
		
		//Print the total number of documents in the corpus
		System.out.println("Total number of documents in the corpus: "+reader.maxDoc());
		
		//Print the number of documents containing the term "new" in <field>TEXT</field>.
		System.out.println("Number of documents containing the term \"new\" for field\"TEXT\": "+reader.docFreq(new Term("TEXT", "new")));
		
		//Print the total number of occurrences of the term "new" across all documents for <field>TEXT</field>.
		System.out.println("Number of occurrences of \"new\" in the field \"TEXT\":"+reader.totalTermFreq(new Term("TEXT","new")));
		
		Terms vocabulary = MultiFields.getTerms(reader, "TEXT");
		
		//Print the size of the vocabulary for <field>TEXT</field>, applicable when the index has only one segment.
		System.out.println("Size of the vocabulary for this field: "+vocabulary.size());
		
		//Print the total number of documents that have at least one term for <field>TEXT</field>
		System.out.println("Number of documents that have at least one term for this field: "+vocabulary.getDocCount());
		
		//Print the total number of tokens for <field>TEXT</field>
		System.out.println("Number of tokens for this field:"+vocabulary.getSumTotalTermFreq());
		
		//Print the total number of postings for <field>TEXT</field>
		System.out.println("Number of postings for this field:"+vocabulary.getSumDocFreq());
		
		//Print the vocabulary for <field>TEXT</field> to file
		TermsEnum iterator = vocabulary.iterator();
		BytesRef byteRef = null;
		FileWriter vocabToFile = new FileWriter(indexPath.concat("vocab.txt"));
		System.out.println("\n*******Vocabulary-Start**********");
		while((byteRef = iterator.next()) != null) {
			String term = byteRef.utf8ToString();
			vocabToFile.write(term);
			vocabToFile.write("\n");
		}
		vocabToFile.close();
		System.out.println("Vocabulary is output to file " + indexPath.concat("vocab.txt"));
		System.out.println("\n*******Vocabulary-End**********");
		reader.close();
	}


}
