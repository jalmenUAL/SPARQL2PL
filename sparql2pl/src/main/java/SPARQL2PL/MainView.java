package SPARQL2PL;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Scanner;
import java.util.Set;

import org.apache.jena.atlas.json.JSON;
import org.apache.jena.atlas.json.JsonArray;
import org.apache.jena.atlas.json.JsonValue;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.util.FileUtils;
import org.jpl7.Atom;
import org.jpl7.Term;

import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.Html;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.contextmenu.MenuItem;
import com.vaadin.flow.component.contextmenu.SubMenu;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Label;
import com.vaadin.flow.component.menubar.MenuBar;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.radiobutton.RadioGroupVariant;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.PWA;
import com.vaadin.flow.server.VaadinSession;

import de.f0rce.ace.AceEditor;
import de.f0rce.ace.enums.AceMode;
import de.f0rce.ace.enums.AceTheme;

/**
 * A sample Vaadin view class.
 * <p>
 * To implement a Vaadin view just extend any Vaadin component and use @Route
 * annotation to announce it in a URL as a Spring managed bean. Use the @PWA
 * annotation make the application installable on phones, tablets and some
 * desktop browsers.
 * <p>
 * A new instance of this class is created for every new user and every browser
 * tab/window.
 */

@Route
@PWA(name = "Vaadin Application", shortName = "Vaadin App", description = "This is an example Vaadin application.", enableInstallPrompt = false)
@CssImport("./styles/shared-styles.css")
@CssImport(value = "./styles/vaadin-text-field-styles.css", themeFor = "vaadin-text-field")
public class MainView extends VerticalLayout {

	TextField file = new TextField();
	Integer step = 0;

	//String food = "C:\\Users\\Administrator\\git\\SPARQL2PL\\sparql2pl\\food.owl";
	//String social = "C:\\Users\\Administrator\\git\\SPARQL2PL\\sparql2pl\\social.owl";
	String food ="https://minerva.ual.es/sparqlpl/food.owl";
	String social="https://minerva.ual.es/sparqlpl/social.owl";
	
	VerticalLayout ldataset = new VerticalLayout();
	Grid<HashMap<String, RDFNode>> dataset = new Grid<HashMap<String, RDFNode>>();
	OntModel model = ModelFactory.createOntologyModel();
	AceEditor editor = new AceEditor();
	AceEditor editorO = new AceEditor();
	Boolean fuzzy = false;
	String service = "";

	public static String readStringFromURL(String requestURL) throws IOException {
		try (Scanner scanner = new Scanner(new URL(requestURL).openStream(), StandardCharsets.UTF_8.toString())) {
			scanner.useDelimiter("\\A");
			return scanner.hasNext() ? scanner.next() : "";
		}
	}

	@Route("tabs-content")
	public class TabsContent extends Div {

		 
		private final Tab cdt;
		private final Tab turtle;
		private final VerticalLayout content;

		public TabsContent() {
			this.getStyle().set("width", "100%");
			
			cdt = new Tab("Dataset");
			turtle = new Tab("Turtle View");

			Tabs tabs = new Tabs(cdt, turtle);
			tabs.addSelectedChangeListener(event -> setContent(event.getSelectedTab()));

			content = new VerticalLayout();
			content.setSpacing(false);
			setContent(tabs.getSelectedTab());

			add(tabs, content);
		}

		private void setContent(Tab tab) {
			content.removeAll();

			 if (tab.equals(cdt)) {
				content.add(ldataset);
			} else {
				content.add(editorO);
			}
		}

	}

	public MainView() {

		VaadinSession.getCurrent().setErrorHandler(new CustomErrorHandler());

		final VerticalLayout layout = new VerticalLayout();
		layout.getStyle().set("width", "100%");
		layout.getStyle().set("background", "#F8F8F8");

		Image lab = new Image("img/bannerspl.png", "banner");
		lab.setWidth("100%");
		lab.setHeight("200px");

		HorizontalLayout lfile = new HorizontalLayout();
		Label ds = new Label("URL Dataset (Type one or Select an Example)");

		TextField file = new TextField();
		file.setWidth("100%");

		Button download = new Button("Load Dataset");
		download.getStyle().set("width", "100pt");

		lfile.add(file);
		lfile.add(download);
		lfile.getStyle().set("width", "100%");

		TabsContent tabs = new TabsContent();
		RadioButtonGroup<String> radioGroup = new RadioButtonGroup<>();
		radioGroup.addThemeVariants(RadioGroupVariant.LUMO_VERTICAL);
		radioGroup.setLabel("Select Type of Resource");
		radioGroup.setItems("RDF/XML", "TURTLE");
		radioGroup.getStyle().set("font-size", "80%");
		radioGroup.setValue("RDF/XML");

		Label fsaq = new Label();
		fsaq.add(new Html("<b style='font-size:150%; background:black; color:white;'>SPARQL Query</b>"));
		Label dt = new Label();
		dt.add(new Html("<b style='font-size:150%; background:black; color:white;'>Dataset</b>"));
		Label cdt = new Label();
		cdt.add(new Html("<b style='font-size:150%; background:black; color:white;'>Dataset</b>"));
		Label cv = new Label();
		cv.add(new Html("<b style='font-size:150%; background:black; color:white;'>Prolog encoding</b>"));

		Button run = new Button("Execute");
		run.setWidth("100%");
		run.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

		VerticalLayout edS = new VerticalLayout();
		VerticalLayout edP = new VerticalLayout();

		editor.setHeight("300px");
		editor.setWidth("100%");
		editor.setFontSize(18);
		editor.setMode(AceMode.sparql);
		editor.setTheme(AceTheme.eclipse);
		editor.setUseWorker(true);
		editor.setReadOnly(false);
		editor.setShowInvisibles(false);
		editor.setShowGutter(false);
		editor.setShowPrintMargin(false);
		editor.setSofttabs(false);

		editorO.setHeight("300px");
		editorO.setWidth("100%");
		editorO.setFontSize(18);
		editorO.setMode(AceMode.turtle);
		editorO.setTheme(AceTheme.eclipse);
		editorO.setUseWorker(true);
		editorO.setReadOnly(false);
		editorO.setShowInvisibles(false);
		editorO.setShowGutter(false);
		editorO.setShowPrintMargin(false);
		editorO.setSofttabs(false);
		
		 

		 

		String foodA = "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\r\n"
				
				
				+ "PREFIX fd:<http://www.semanticweb.org/food#>\r\n" 
				+ "SELECT ?u\r\n" 
				+ "WHERE {\r\n"
				+ "	?u rdf:type fd:food .\r\n" 
				+ "	?u fd:made_from fd:flour \r\n" + "}";

		String foodB = "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\r\n"
				
				+ "PREFIX fd:<http://www.semanticweb.org/food#>\r\n" 
				+ "SELECT ?u\r\n" 
				+ "WHERE {\r\n"
				+ "	?u rdf:type fd:food .\r\n" 
				+ "	?u fd:time ?t .\r\n" 
				+ "FILTER (?t < 30 || ?t > 60)\r\n" + "}";

		String foodC = "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\r\n"
				
				+ "PREFIX fd:<http://www.semanticweb.org/food#>\r\n" 
				+ "SELECT ?u\r\n" + "WHERE {\r\n"
				+ "	?u rdf:type fd:food .\r\n" 
				+ "	?u fd:season fd:salt .\r\n" 
				+ "	{SELECT ?u (count(*) as ?l)\r\n"
				+ "		WHERE { ?u fd:made_from ?m .\r\n"
				+ "			 ?u fd:time ?t .\r\n "
				+ "		FILTER(?t<60) . FILTER(?t>0)}\r\n" 
				+ "GROUP BY ?u}\r\n"
				+ "FILTER(?l > 3) .\r\n" + "}";

		
		String foodD = "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\r\n"
				
				+ "PREFIX fd:<http://www.semanticweb.org/food#>\r\n" 
				+ "SELECT ?m\r\n" 
				+ "WHERE\r\n" 
				+ "{\r\n"
				+ "	?m rdf:type fd:menu .\r\n" 
				+ "	?m fd:price ?p\r\n" 
				+ "	FILTER(?p>=100) .\r\n" 
				+ "	FILTER EXISTS\r\n"
				+ "	{\r\n" 
				+ "		SELECT ?m ?d ?t ?ni\r\n"
				+ "			WHERE { ?m fd:dish ?d . ?d fd:time ?t .\r\n" 
				+ "			FILTER(?t <60) .\r\n"
				+ "			{\r\n"
				+ "			SELECT ?d (count(*) as ?ni)\r\n"
				+ "			WHERE { ?d fd:made_from ?i}\r\n" 
				+ "			GROUP BY ?d\r\n" + "}\r\n"
				+ "	FILTER(?ni <= 2)\r\n" 
				+ "}" + "}" + "}";

		String foodE = "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\r\n"
				
				+ "PREFIX fd:<http://www.semanticweb.org/food#>\r\n" 
				+ "SELECT ?c (sum(?t) as ?tt)\r\n" 
				+ "WHERE {\r\n"
				+ "	?u rdf:type fd:food .\r\n" 
				+ "	?u fd:cooked ?c .\r\n" 
				+ "	?u fd:time ?t .\r\n" 
				+ "}\r\n"
				+ "GROUP BY ?c";

		String foodF = "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\r\n"
				
				+ "PREFIX fd:<http://www.semanticweb.org/food#>\r\n" 
				+ "SELECT ?u " 
				+ "WHERE {\r\n"
				+ "	?u rdf:type fd:food .\r\n" 
				+ "	?u fd:cooked fd:raw .\r\n" 
				+ "	?u fd:time ?t .\r\n"
				+ "	OPTIONAL {?u fd:made_from fd:milk}\r\n" 
				+ "	FILTER(?t >= 60)\r\n" + "}";

		String foodG = "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\r\n"
				
				+ "PREFIX fd:<http://www.semanticweb.org/food#>\r\n" 
				+ "SELECT ?u " 
				+ "WHERE {\r\n"
				+ "	?u rdf:type fd:food .\r\n" 
				+ "	?u fd:cooked fd:raw .\r\n" 
				+ "	?u fd:time ?t .\r\n"
				+ "	MINUS {?u fd:made_from fd:milk}\r\n" 
				+ "	FILTER(?t >= 60)\r\n" + "}";

		 
		String foodH = "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\r\n"
				
				+ "PREFIX fd:<http://www.semanticweb.org/food#>\r\n" 
				+ "SELECT ?m\r\n" 
				+ "WHERE\r\n" 
				+ "{\r\n"
				+ "	?m rdf:type fd:menu .\r\n" 
				+ "	?m fd:price ?p\r\n" 
				+ "	FILTER(?p>=10) .\r\n" 
				+ "	FILTER NOT EXISTS\r\n"
				+ "		{\r\n" 
				+ "			SELECT ?m ?d ?t ?ni\r\n"
				+ "			WHERE { ?m fd:dish ?d . ?d fd:time ?t .\r\n" 
				+ "			FILTER(?t >60) .\r\n"
				+ "		{\r\n"
				+ "			SELECT ?d (count(*) as ?ni)\r\n"
				+ "			WHERE { ?d fd:made_from ?i}\r\n" 
				+ "			GROUP BY ?d\r\n" 
				+ "}\r\n"
				+ "FILTER(?ni <= 2)\r\n" 
				+ "}" + "}" + "}";

		String foodI = "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\r\n"
				
				+ "PREFIX fd:<http://www.semanticweb.org/food#>\r\n" 
				+ "SELECT ?m WHERE {\r\n"
				+ "{?m fd:cooked fd:raw }\r\n" 
				+ "	UNION {\r\n" 
				+ "{?m fd:cooked fd:roast }\r\n" 
				+ "	UNION\r\n"
				+ "{?m fd:cooked fd:bake } } }";
		
		String socialA =   "PREFIX rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\r\n" 
				 
				+ "PREFIX sn: <http://www.semanticweb.org/social#>\r\n"
				 
				+ "SELECT ?user WHERE {\r\n"
				+ "	?user rdf:type sn:User .\r\n "
				+ "	?user sn:age 51\r\n " 
				+ "} ";
		
		String socialB =   "PREFIX rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\r\n"
				+ "PREFIX sn: <http://www.semanticweb.org/social#>\r\n"
				+ "SELECT ?user ?user2 WHERE\r\n "
				+ "{\n"
				+ " ?user rdf:type sn:User .\r\n"
				+ " ?user2 rdf:type sn:User .\r\n"
				+ " ?user sn:friend_of ?user2 }";
		
		String socialC =  "PREFIX rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n"
				+ "PREFIX sn: <http://www.semanticweb.org/social#>\n"
				+ "SELECT ?nuser   WHERE\n "
				+ "{\n"
				+ "	?user rdf:type sn:User .\n"
				+ "	?user sn:name ?nuser .\n "				 
				+ "	?user sn:age ?age .\n "			  
				+ "	FILTER(?age > 40 )}\n"; 
				 
		
		String socialD =   "PREFIX rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n"
				+ "PREFIX sn: <http://www.semanticweb.org/social#>\n"
				+ "SELECT ?user ?event WHERE\n "
				+ "{\n" 
				+ "	?user rdf:type sn:User .\n" 
				+ "	?user sn:age ?age .\n"
				+ "	FILTER (?age > 40) .\n" 
				+ "	?user sn:attends_to ?event\r\n" 
				+ "}\n";
		
		String socialE =  "PREFIX rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n" 
				 
				+ "PREFIX sn: <http://www.semanticweb.org/social#>\n"
				 
				+ "SELECT ?user ?age ?event WHERE\n "
				+ "{\n"
				+ "	?user rdf:type sn:User .\n" 
				+ " ?user sn:age ?age .\n" 
				+ "OPTIONAL {\r\n"
				+ "	 SELECT ?user ?event WHERE { ?user sn:attends_to ?event } } " + "} ";
		
		String socialF =  "PREFIX rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n"
				+ "PREFIX sn: <http://www.semanticweb.org/social#>\n"
				+ "SELECT ?user WHERE {\n" 
				+ "?user rdf:type sn:User .\n"
				+ "?user sn:age ?age .\n" 
				+ "FILTER (?age > 25) .\n"
				+ "FILTER NOT EXISTS\r\n"
				+ " {	SELECT ?user WHERE {\n" 
				+ "			?user sn:attends_to ?event" + "}" + "}}";
		
		String socialG =  "PREFIX rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#>"
				+ "PREFIX sn: <http://www.semanticweb.org/social#>\n" 
				+ "SELECT ?user WHERE\n "
				+ "{ ?user rdf:type sn:User .\n "
				+ "MINUS { ?user sn:attends_to ?event } }";
		
		String socialH =  "PREFIX rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n"
				+ "PREFIX sn: <http://www.semanticweb.org/social#>\n"
				+ "SELECT ?user WHERE {\n" 
				+ "	?user rdf:type sn:User .\n"
				+ "	?user sn:age ?age .\n" 
				+ "	FILTER (?age > 25) .\n"
				+ "	FILTER NOT EXISTS\r\n"
				+ "	 {SELECT ?age WHERE {\n" 
				+ "			?user2 rdf:type sn:User .\n"
				+ "			?user2 sn:age ?age2 .\n" 
				+ "			FILTER (?age < ?age2 ) }\n"
				+ "}}\n";
		
		String socialI =  "PREFIX rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n"
				+ "PREFIX sn: <http://www.semanticweb.org/social#>\n"
				+ "SELECT ?user (count(*) AS ?areg)  \r\n" 
				+ "WHERE {\r\n" 
				+ "	?user rdf:type sn:User .\n"
				+ "	?conf sn:added_by ?user }\r\n" 
				+ "GROUP BY ?user \r\n";

		AceEditor editorP = new AceEditor();
		editorP.setHeight("300px");
		editorP.setWidth("100%");
		editorP.setFontSize(18);
		editorP.setMode(AceMode.prolog);
		editorP.setTheme(AceTheme.eclipse);
		editorP.setUseWorker(true);
		editorP.setReadOnly(true);
		editorP.setShowInvisibles(false);
		editorP.setShowGutter(false);
		editorP.setShowPrintMargin(false);
		editorP.setSofttabs(false);
		Grid<HashMap<String, Term>> answers = new Grid<HashMap<String, Term>>();
		answers.setWidth("100%");
		answers.setHeight("100%");
		answers.setVisible(true);

		 

		download.addClickListener(event -> {
			if (radioGroup.getValue() == "RDF/XML") {
				load_rdf(file.getValue());
				show_rdf();
			} else {
				load_ttl(file.getValue());
				show_rdf();
			}

		});

		 
		ldataset.setWidth("100%");
		ldataset.setHeight("200pt");
		dataset.setWidth("100%");
		dataset.setHeight("100%");
		dataset.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);

		file.setValue(food);

		load_rdf(file.getValue());
		show_rdf();
		editor.setValue(foodA);

	
		MenuBar menuBar = new MenuBar();
		menuBar.setWidth("100%");
		ComponentEventListener<ClickEvent<MenuItem>> listener = e -> {
			
			answers.removeAllColumns();
			editorP.clear();
			
			if (e.getSource().getText().equals("Food A")) {
				file.setValue(food);
				radioGroup.setValue("RDF/XML");

				cv.setVisible(false);
				load_rdf(file.getValue());
				show_rdf();
				editor.setValue(foodA);
				 

			} else if (e.getSource().getText().equals("Food B")) {
				file.setValue(food);
				radioGroup.setValue("RDF/XML");

				cv.setVisible(false);
				load_rdf(file.getValue());
				show_rdf();
				editor.setValue(foodB);
				 

			} else if (e.getSource().getText().equals("Food C")) {
				file.setValue(food);
				radioGroup.setValue("RDF/XML");

				cv.setVisible(false);
				load_rdf(file.getValue());
				show_rdf();
				editor.setValue(foodC);
				 

			} else if (e.getSource().getText().equals("Food D")) {
				file.setValue(food);
				radioGroup.setValue("RDF/XML");

				cv.setVisible(false);
				load_rdf(file.getValue());
				show_rdf();
				editor.setValue(foodD);
				 

			} else if (e.getSource().getText().equals("Food E")) {
				file.setValue(food);
				radioGroup.setValue("RDF/XML");

				cv.setVisible(false);
				load_rdf(file.getValue());
				show_rdf();
				editor.setValue(foodE);
				 

			} else if (e.getSource().getText().equals("Food F")) {
				file.setValue(food);
				radioGroup.setValue("RDF/XML");

				cv.setVisible(false);
				load_rdf(file.getValue());
				show_rdf();
				editor.setValue(foodF);
				 

			}
		 else if (e.getSource().getText().equals("Food G")) {
			file.setValue(food);
			radioGroup.setValue("RDF/XML");

			cv.setVisible(false);
			load_rdf(file.getValue());
			show_rdf();
			editor.setValue(foodG);
			 

		
	} else if (e.getSource().getText().equals("Food H")) {
		file.setValue(food);
		radioGroup.setValue("RDF/XML");

		cv.setVisible(false);
		load_rdf(file.getValue());
		show_rdf();
		editor.setValue(foodH);
		 

	
} else if (e.getSource().getText().equals("Food I")) {
	file.setValue(food);
	radioGroup.setValue("RDF/XML");

	cv.setVisible(false);
	load_rdf(file.getValue());
	show_rdf();
	editor.setValue(foodI);
	 

} else
			if (e.getSource().getText().equals("Social A")) {
				file.setValue(social);
				radioGroup.setValue("RDF/XML");

				cv.setVisible(false);
				load_rdf(file.getValue());
				show_rdf();
				editor.setValue(socialA);
				 

			} else if (e.getSource().getText().equals("Social B")) {
				file.setValue(social);
				radioGroup.setValue("RDF/XML");

				cv.setVisible(false);
				load_rdf(file.getValue());
				show_rdf();
				editor.setValue(socialB);
				 

			} else if (e.getSource().getText().equals("Social C")) {
				file.setValue(social);
				radioGroup.setValue("RDF/XML");

				cv.setVisible(false);
				load_rdf(file.getValue());
				show_rdf();
				editor.setValue(socialC);
				 

			} else if (e.getSource().getText().equals("Social D")) {
				file.setValue(social);
				radioGroup.setValue("RDF/XML");

				cv.setVisible(false);
				load_rdf(file.getValue());
				show_rdf();
				editor.setValue(socialD);
				 

			} else if (e.getSource().getText().equals("Social E")) {
				file.setValue(social);
				radioGroup.setValue("RDF/XML");

				cv.setVisible(false);
				load_rdf(file.getValue());
				show_rdf();
				editor.setValue(socialE);
				 

			} else if (e.getSource().getText().equals("Social F")) {
				file.setValue(social);
				radioGroup.setValue("RDF/XML");

				cv.setVisible(false);
				load_rdf(file.getValue());
				show_rdf();
				editor.setValue(socialF);
				 

			}
		 else if (e.getSource().getText().equals("Social G")) {
			file.setValue(social);
			radioGroup.setValue("RDF/XML");

			cv.setVisible(false);
			load_rdf(file.getValue());
			show_rdf();
			editor.setValue(socialG);
			 

		
	} else if (e.getSource().getText().equals("Social H")) {
		file.setValue(social);
		radioGroup.setValue("RDF/XML");

		cv.setVisible(false);
		load_rdf(file.getValue());
		show_rdf();
		editor.setValue(socialH);
		 

	
} else if (e.getSource().getText().equals("Social I")) {
	file.setValue(social);
	radioGroup.setValue("RDF/XML");

	cv.setVisible(false);
	load_rdf(file.getValue());
	show_rdf();
	editor.setValue(socialI);
	 

}

		}

		;

		MenuItem food = menuBar.addItem("Food Examples", listener);
		SubMenu basicSubMenu = food.getSubMenu();
		basicSubMenu.addItem("Food A", listener);
		basicSubMenu.addItem("Food B", listener);
		basicSubMenu.addItem("Food C", listener);
		basicSubMenu.addItem("Food D", listener);
		basicSubMenu.addItem("Food E", listener);
		basicSubMenu.addItem("Food F", listener);
		basicSubMenu.addItem("Food G", listener);
		basicSubMenu.addItem("Food H", listener);
		basicSubMenu.addItem("Food I", listener);
		MenuItem social = menuBar.addItem("Social Network Examples", listener);
		SubMenu basicSubMenu2 = social.getSubMenu();
		basicSubMenu2.addItem("Social A", listener);
		basicSubMenu2.addItem("Social B", listener);
		basicSubMenu2.addItem("Social C", listener);
		basicSubMenu2.addItem("Social D", listener);
		basicSubMenu2.addItem("Social E", listener);
		basicSubMenu2.addItem("Social F", listener);
		basicSubMenu2.addItem("Social G", listener);
		basicSubMenu2.addItem("Social H", listener);
		basicSubMenu2.addItem("Social I", listener);
		 
		 

		VerticalLayout lanswers = new VerticalLayout();
		lanswers.setWidth("100%");
		lanswers.setHeight("200pt");
		 
		lanswers.setVisible(true);

		run.addClickListener(new ComponentEventListener<ClickEvent<Button>>() {

			@Override
			public void onComponentEvent(ClickEvent<Button> event) {
				// TODO Auto-generated method stub
				step = 0; // step++;
				pSPARQL ps = new pSPARQL();				
				String s = editor.getValue();
				List<List<String>> rules = null;
				rules = ps.SPARQLtoProlog(s, step);

				String pp = "";
				String prule = "";
				for (List<String> r : rules) {
					prule = r.get(0) + ":-";
					for (int i = 1; i < r.size(); i++) {
						prule = prule + "\n       " + r.get(i) + ",";
					}
					prule = prule.substring(0, prule.length() - 1) + ".";
					pp = pp + "\n" + prule;

				}

				 

				String t1 = "use_module(library(semweb/rdf11))";
				org.jpl7.Query q1 = new org.jpl7.Query(t1);
				System.out.print((q1.hasSolution() ? "" : ""));
				q1.close();

				String t11 = "use_module(library(semweb/rdf_http_plugin))";
				org.jpl7.Query q11 = new org.jpl7.Query(t11);
				System.out.print((q11.hasSolution() ? "" : ""));
				q11.close();

				String t12 = "use_module(library(lists))";
				org.jpl7.Query q12 = new org.jpl7.Query(t12);
				System.out.print((q12.hasSolution() ? "" : ""));
				q12.close();

				String t21b = "rdf_reset_db";
				org.jpl7.Query q21b = new org.jpl7.Query(t21b);
				System.out.print((q21b.hasSolution() ? "" : ""));
				q21b.close();

				String t21bb = "rdf_reset_db";
				org.jpl7.Query q21bb = new org.jpl7.Query(t21bb);
				System.out.print((q21bb.hasSolution() ? "" : ""));
				q21bb.close();

				String t21c = " working_directory(_,\"C:/\")";
				org.jpl7.Query q21c = new org.jpl7.Query(t21c);
				System.out.print((q21c.hasSolution() ? "" : ""));
				q21c.close();

				String t2 = "rdf_load('" + "C:/tmp-sparql/model.rdf" + "')";
				org.jpl7.Query q2 = new org.jpl7.Query(t2);
				System.out.print((q2.hasSolution() ? "" : ""));
				q2.close();

				 

				 

				editorP.setValue(pp);

				String prule2 = "";
				System.out.println("Number of rules: " + rules.size());
				for (List<String> r : rules) {

					prule2 = r.get(0) + ":-";
					for (int i = 1; i < r.size(); i++) {
						prule2 = prule2 + r.get(i) + ',';
					}
					prule2 = prule2.substring(0, prule2.length() - 1);
					String aprule = "asserta((" + prule2 + "))";
					org.jpl7.Query q3 = new org.jpl7.Query(aprule);
					System.out.println((q3.hasSolution() ? aprule : ""));
					q3.close();

				}

				String[] ops = {
						"'http://www.w3.org/2001/XMLSchema#decimal'(X^^TX,Y^^'http://www.w3.org/2001/XMLSchema#decimal'):-!, Y=X ",
						"'http://jena.apache.org/ARQ/function#sqrt'(X^^TX,Y^^TX):-!, Y is sqrt(X) ",
						"if(X,Y,Z,T):-!,((X=1^^_)->T=Y;T=Z)",
						"call_function(X,Y,F,T):-!, X=..[_,TX,TYPE],Y=..[_,TY|_],NE=..[F,TX,TY],TAUX is NE,T=..['^^',TAUX,'http://www.w3.org/2001/XMLSchema#decimal']" };

				 
				
				 
				
				for (int i = 0; i < ops.length; i++) {
					String aprule = "asserta((" + ops[i] + "))";
					org.jpl7.Query q3 = new org.jpl7.Query(aprule);
					System.out.println((q3.hasSolution() ? aprule : ""));
					q3.close();
				}

				List<HashMap<String, Term>> rows = new ArrayList<>();

				answers.removeAllColumns();

				Atom t = new Atom("Null");
				org.jpl7.Query q3 = new org.jpl7.Query(rules.get(0).get(0));
				Map<String, Term>[] sols = q3.allSolutions();
				q3.close();

				for (Map<String, Term> solution : sols) {
					Set<String> sol = solution.keySet();
					for (String var : sol) {
						if (solution.get(var).isCompound()) {
							solution.put(var, solution.get(var).arg(1));
						}
						if (solution.get(var).isVariable()) {
							solution.put(var, t);
						}
					}
				}

				for (Map<String, Term> solution : sols) {
					rows.add((HashMap<String, Term>) solution);

				}
				System.out.println("Yes: answers " + sols.length);

				answers.setItems(rows);

				if (rows.size() > 0) {
					HashMap<String, Term> sr = rows.get(0);

					for (Map.Entry<String, Term> entry : sr.entrySet()) {
						answers.addColumn(h -> h.get(entry.getKey())).setHeader(entry.getKey());
					}
				}

				for (List<String> r : rules) {

					String dr = r.get(0);
					org.jpl7.Query drq = new org.jpl7.Query("retractall(" + dr + ")");
					System.out.println((drq.hasSolution() ? drq : ""));
					drq.close();

				}

				for (int i = 0; i < ops.length; i++) {
					String aprule = "retract((" + ops[i] + "))";
					org.jpl7.Query q4 = new org.jpl7.Query(aprule);
					System.out.println((q4.hasSolution() ? aprule : ""));
					q4.close();
				}

			}

		});

		edS.add(editor);
		edP.add(editorP);
		layout.add(lab);
		layout.add(new Label("Please select examples in each category..."));
		layout.add(menuBar);
		layout.add(ds);
		layout.add(lfile);
		layout.add(radioGroup);
		layout.add(dt);
		editorO.setReadOnly(true);

		layout.add(tabs);
		layout.add(fsaq);
		layout.add(edS);
		layout.add(run);
		lanswers.add(answers);
		layout.add(lanswers);
		layout.add(cv);
		layout.add(edP);
		cv.setVisible(true);
		editor.setLiveAutocompletion(true);
		editorP.setVisible(true);
		add(layout);
		this.setSizeFull();

	}

	public void show_notification(String type, String message) {
		Notification notification = Notification.show(type + " " + message);
		notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
		notification.setPosition(Notification.Position.MIDDLE);
	}

	public void load_rdf(String url) {
		model.removeAll();
		model.clearNsPrefixMap();
		try {
			model.read(url, "RDF/XML");
		} catch (Exception e) {
			show_notification("Format Error", "The dataset is not in RDF/XML format");
		}
		File theDir = new File("tmp-sparql");
		if (!theDir.exists()) {
			theDir.mkdir();
		}
		String fileName = "C:/tmp-sparql/" + "model.rdf";
		File f = new File(fileName);
		FileOutputStream file;
		try {
			file = new FileOutputStream(f);
			model.writeAll(file, FileUtils.langXML);
			try {
				file.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		} catch (FileNotFoundException e1) {
			e1.printStackTrace();
		}
	}

	public void load_ttl(String url) {
		model.removeAll();
		model.clearNsPrefixMap();
		try {
			model.read(url, "TTL");
		} catch (Exception e) {
			show_notification("Format Error", "The dataset is not in RDF/Turtle format");
		}
		File theDir = new File("tmp-sparql");
		if (!theDir.exists()) {
			theDir.mkdir();
		}
		String fileName = "C:/tmp-sparql/" + "model.rdf";
		File f = new File(fileName);
		FileOutputStream file;
		try {
			file = new FileOutputStream(f);
			model.writeAll(file, FileUtils.langXML);
			try {
				file.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		} catch (FileNotFoundException e1) {
			e1.printStackTrace();
		}
	}

	public void autocompletion() {

		List<String> l = new ArrayList<String>();	 
		l.add("SELECT");
		l.add("WHERE");
		l.add("FILTER");
		l.add("HAVING");
		l.add("BIND");
		l.add("ORDER BY");
		l.add("LET");
		editor.setCustomAutocompletion(l);

	}

	public void show_rdf() {

		List<String> l = new ArrayList<String>();	 
		l.add("SELECT");
		l.add("WHERE");
		l.add("FILTER");
		l.add("HAVING");
		l.add("BIND");
		l.add("ORDER BY");
		l.add("VALUES");
		l.add("LET");

		Map<String, String> prefix = model.getNsPrefixMap();

		String query_load = "";

		for (String p : prefix.keySet()) {
			if (!p.equals("")) {
				query_load = query_load + "PREFIX " + p + ": " + "<" + prefix.get(p) + ">\r\n";
			}
		}

		 query_load = query_load + "SELECT ?s ?p ?o\r\n WHERE\r\n {\r\n ?s ?p ?o\r\n }\r\n";

		editor.setValue(query_load);

		 

		String crisp = 
				"PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\r\n"
				+ "PREFIX owl: <http://www.w3.org/2002/07/owl#>\r\n"
				+ "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>\r\n"
				+ "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\r\n" 
				+ "PREFIX json: <http://www.json.org#>\r\n"
				+ "SELECT ?Subject ?Property ?Object WHERE { ?Subject ?Property ?Object . \n"
				+ " }" 
				+ " ORDER BY ?Property";

		 

		
		List<HashMap<String, RDFNode>> rows_dataset_crisp = new ArrayList<>(); 
		Query query_crisp = QueryFactory.create(crisp);
		ResultSet result_crisp = (ResultSet) QueryExecutionFactory.create(query_crisp, model).execSelect();
		dataset.removeAllColumns();
		List<String> variables_crisp = result_crisp.getResultVars();
		rows_dataset_crisp.clear();
		 

		while (result_crisp.hasNext()) {
			QuerySolution solution = result_crisp.next();
			LinkedHashMap<String, RDFNode> sol = new LinkedHashMap<String, RDFNode>();
			for (String vari : variables_crisp) {
				sol.put(vari, solution.get(vari));
				if (solution.get(vari).isURIResource()) {
					if (model.getNsURIPrefix(solution.get(vari).asNode().getNameSpace()) == null) {
						l.add(solution.get(vari).asNode().getLocalName());
					} else {
						l.add(model.getNsURIPrefix(solution.get(vari).asNode().getNameSpace()) + ":"
								+ solution.get(vari).asNode().getLocalName());
					}
				}

			}
			rows_dataset_crisp.add(sol);
		}

		 

		if (rows_dataset_crisp.size() > 0) {
			ldataset.setVisible(true);
			dataset.setVisible(true);
			HashMap<String, RDFNode> sr = rows_dataset_crisp.get(0);
			for (Map.Entry<String, RDFNode> entry : sr.entrySet()) {
				dataset.addColumn(h -> h.get(entry.getKey())).setHeader(entry.getKey()).setAutoWidth(true)
						.setResizable(true).setSortable(true)
						.setComparator((x, y) -> isNumeric(x.get(entry.getKey()).toString())
								& isNumeric(y.get(entry.getKey()).toString())
										? Float.compare(Float.parseFloat(x.get(entry.getKey()).toString()),
												Float.parseFloat(y.get(entry.getKey()).toString()))
										: x.get(entry.getKey()).toString().compareTo(y.get(entry.getKey()).toString()));
			}
		} else {
			show_notification("Downloaded!", "This crisp dataset is empty!");
		}
		dataset.setItems(rows_dataset_crisp);
 
		ldataset.add(dataset);
		editor.setCustomAutocompletion(l);

		String fileName = "C:/tmp-sparql/" + "model.rdf";
		File f = new File(fileName);
		FileOutputStream file;
		try {
			file = new FileOutputStream(f);
			model.writeAll(file, FileUtils.langXML);
			try {
				file.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		} catch (FileNotFoundException e1) {
			e1.printStackTrace();
		}

		String content = "";
		String file2 = "C:/tmp-sparql/" + "model.rdf";
		Path path = Paths.get(file2);

		try {
			content = Files.readString(path);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		editorO.setValue(content);

	}

	public void load_tree(JsonValue l, String root) {

		if (l.isNumber()) {
		} else if (l.isBoolean()) {
		} else if (l.isString()) {
		} else if (l.isObject()) {

			for (Entry<String, JsonValue> e : l.getAsObject().entrySet()) {

				model.add(ResourceFactory.createResource("http://www.json.org#" + root),
						ResourceFactory.createProperty("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"),
						ResourceFactory.createResource("http://www.w3.org/2002/07/owl#NamedIndividual"));

				if (e.getValue().isPrimitive()) {

					if (e.getValue().toString().equals(" ")) {
					} else {
						model.add(ResourceFactory.createProperty("http://www.json.org#" + e.getKey()),
								ResourceFactory.createProperty("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"),
								ResourceFactory.createResource("http://www.w3.org/2002/07/owl#DataProperty"));

						model.add(ResourceFactory.createResource("http://www.json.org#" + root),
								ResourceFactory.createProperty("http://www.json.org#" + e.getKey()),
								ResourceFactory.createPlainLiteral(e.getValue().toString().replace("\"", "")));

					}

				} else

				{
					model.add(ResourceFactory.createProperty("http://www.json.org#" + e.getKey()),
							ResourceFactory.createProperty("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"),
							ResourceFactory.createResource("http://www.w3.org/2002/07/owl#ObjectProperty"));

					model.add(ResourceFactory.createResource("http://www.json.org#" + root),
							ResourceFactory.createProperty("http://www.json.org#" + e.getKey()),
							ResourceFactory.createResource("http://www.json.org#" + root + e.getKey()));

					load_tree(e.getValue(), root + e.getKey());
				}

			}
		}

		else if (l.isArray()) {
			JsonArray children = l.getAsArray();

			for (JsonValue e : children) {
				if (root == "") {
					load_tree(e, root + e.hashCode());
					model.add(ResourceFactory.createResource("http://www.json.org#" + root + e.hashCode()),
							ResourceFactory.createProperty("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"),
							ResourceFactory.createResource("http://www.json.org#item"));

				} else
					load_tree(e, root);

			}
		}

	}

	public void load_json(String url) {

		InputStream input;
		try {
			input = new URL(url).openStream();
			JsonValue e = null;
			try {
				e = JSON.parseAny(input);
			} catch (Exception e1) {
				// TODO Auto-generated catch block
				show_notification("Format Error", "The dataset is not in JSON format");
			}
			model.removeAll();
			model.clearNsPrefixMap();
			model.setNsPrefix("rdfs", "http://www.w3.org/2000/01/rdf-schema#");
			model.setNsPrefix("rdf", "http://www.w3.org/1999/02/22-rdf-syntax-ns#");
			load_tree(e, "");

		} catch (MalformedURLException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();

		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();

		}
		File theDir = new File("tmp-sparql");
		if (!theDir.exists()) {
			theDir.mkdir();
		}
		String fileName = "C:/tmp-sparql/" + "model.rdf";
		File f = new File(fileName);
		FileOutputStream file;
		try {
			file = new FileOutputStream(f);
			model.writeAll(file, FileUtils.langXML);
			try {
				file.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		} catch (FileNotFoundException e1) {
			e1.printStackTrace();
		}

	}

	public static boolean isNumeric(String strNum) {
		if (strNum == null) {
			return false;
		}
		try {
			Float.parseFloat(strNum);
		} catch (NumberFormatException nfe) {
			return false;
		}
		return true;
	}

}
