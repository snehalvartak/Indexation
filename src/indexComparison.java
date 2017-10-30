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
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.analysis.core.SimpleAnalyzer;
import org.apache.lucene.analysis.core.StopAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.MultiFields;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;

public class indexComparison {

	//Define the Path to index and corpus directory 
	private static final String indexPath = "D:/IU/Search_workspace/compare_index/";
	private static final String corpusPath = "D:/IU/Search_workspace/corpus";

	public static void main(String[] args) throws JDOMException {

		System.out.println("Create Index..");		

		try {
				for(int i=0; i < 4;i++) {
					//Calls method to create index for each type of analyzer
					String analyzer_type = indexWriter(i);
					//Print the type of analyzer in use
					System.out.println("\n******"+analyzer_type+"******");
					// Calls method to generate statistics for each index
					getStats(analyzer_type);
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	}

	// Method to Define the IndexWriterConfig
	private static String indexWriter(int index_type) throws IOException, JDOMException {
		Analyzer analyzer= null;
		String analyzer_type =null;
		//Decide which analyzer to use based on the integer passed to indexWriter()
		switch(index_type) {
			case 0:
				analyzer = new StandardAnalyzer();
				analyzer_type ="STANDARD_ANALYZER";
				break;
			case 1:
				analyzer = new KeywordAnalyzer();
				analyzer_type = "KEYWORD_ANALYZER";
				break;
			case 2:
				analyzer = new SimpleAnalyzer();
				analyzer_type = "SIMPLE_ANALYZER";
				break;
			case 3:
				analyzer = new StopAnalyzer();
				analyzer_type = "STOP_ANALYZER";
				break;
		}
		
		//Create a separate folder for each analyzer index
		Directory dir = FSDirectory.open(Paths.get(indexPath.concat(analyzer_type)));
		IndexWriterConfig iwc = new IndexWriterConfig(analyzer);
		iwc.setOpenMode(OpenMode.CREATE);
		IndexWriter writer = new IndexWriter(dir, iwc);
		File[] listFiles =new File(corpusPath).listFiles();

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
					is.close();
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
							if(result.get("TEXT")!=null) {
								// Use a textField since we can the content between the tags to be indexed and indexed.
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
		// Return analyzer_type to be used to read the index for statistics
		return analyzer_type;
	}

	// Method to extract text between tags <TEXT></TEXT> for each document using SAX Parser
	private static HashMap<String, String> trecParse(String doc) throws JDOMException, IOException {
		//Define String variables to extract content between tags
		String text ="";

		// Store the tag and corresponding text to a HashMap
		HashMap<String, String> document = new HashMap<String, String>();

		//Using SAX Builder to Parse XML Styled Document
		SAXBuilder saxBuilder = new SAXBuilder(); 
		org.jdom2.Document xmlDoc = saxBuilder.build(new StringReader(doc));
		//Get the root element
		Element root = xmlDoc.getRootElement();

		// Get the <TEXT> element
		// For each element with node <TEXT> append to the same string
		if(root.getChildren("TEXT").size() >= 1) {
			for (Element texts: root.getChildren("TEXT")) {
				text = text + " " + texts.getText();
			}
			document.put("TEXT",text.trim());
		}
		//Return parsed text
		return document;
	}


	//This method prints out statistics relevant to the index
	private static void getStats(String analyzer_type) throws IOException {
		IndexReader reader = DirectoryReader.open(FSDirectory.open(Paths.get(indexPath.concat(analyzer_type))));
		
		//Print the total number of documents in the corpus
		System.out.println("Total number of documents in the corpus: "+reader.maxDoc());
		
		//Print the number of documents containing the term "the"(stop word) in <field>TEXT</field>.
		System.out.println("Number of documents containing the term \"the\" for field\"TEXT\": "+reader.docFreq(new Term("TEXT", "the")));
		
		//Print the total number of occurrences of the term "the"(stop word) across all documents for <field>TEXT</field>.
		System.out.println("Number of occurrences of \"the\"(stop word) in the field \"TEXT\":"+reader.totalTermFreq(new Term("TEXT","the")));
		
		Terms vocabulary = MultiFields.getTerms(reader, "TEXT");
		//Print the size of the vocabulary for <field>TEXT</field>, applicable when the index has only one segment.
		System.out.println("Size of the vocabulary for this field: "+vocabulary.size());
		
		//Print the total number of documents that have at least one term for <field>TEXT</field>
		System.out.println("Number of documents that have at least one term for this field: "+vocabulary.getDocCount());

		//Print the total number of tokens for <field>TEXT</field>
		System.out.println("Number of tokens for this field:"+vocabulary.getSumTotalTermFreq());
		
		//Print the total number of postings for <field>TEXT</field>
		System.out.println("Number of postings for this field:"+vocabulary.getSumDocFreq());
		
		//Write vocabulary for <field>TEXT</field> to File
		TermsEnum iterator = vocabulary.iterator();
		BytesRef byteRef = null;
		FileWriter vocabToFile = new FileWriter(indexPath.concat(analyzer_type+".txt"));
		System.out.println("\n*******Vocabulary-Start**********");
		while((byteRef = iterator.next()) != null) {
			String term = byteRef.utf8ToString();
			vocabToFile.write(term);
			vocabToFile.write("\n");
		}
		vocabToFile.close();
		System.out.println("Vocabulary is output to file " + indexPath.concat(analyzer_type+".txt"));
		System.out.println("\n*******Vocabulary-End**********");
		reader.close();
	}


}
