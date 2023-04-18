package SPARQL2PL;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Scanner;
import java.util.Set;
import java.util.Stack;

import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.ResultSet;
import org.apache.jena.query.ResultSetFormatter;
import org.apache.jena.query.SortCondition;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.sparql.core.TriplePath;
import org.apache.jena.sparql.core.Var;
import org.apache.jena.sparql.engine.binding.Binding;
import org.apache.jena.sparql.expr.E_Equals;
import org.apache.jena.sparql.expr.Expr;
import org.apache.jena.sparql.expr.ExprAggregator;
import org.apache.jena.sparql.expr.ExprFunctionOp;
import org.apache.jena.sparql.expr.ExprVar;
import org.apache.jena.sparql.syntax.Element;
import org.apache.jena.sparql.syntax.ElementAssign;
import org.apache.jena.sparql.syntax.ElementBind;
import org.apache.jena.sparql.syntax.ElementData;
import org.apache.jena.sparql.syntax.ElementExists;
import org.apache.jena.sparql.syntax.ElementFilter;
import org.apache.jena.sparql.syntax.ElementGroup;
import org.apache.jena.sparql.syntax.ElementMinus;
import org.apache.jena.sparql.syntax.ElementNotExists;
import org.apache.jena.sparql.syntax.ElementOptional;
import org.apache.jena.sparql.syntax.ElementPathBlock;
import org.apache.jena.sparql.syntax.ElementSubQuery;
import org.apache.jena.sparql.syntax.ElementUnion;
import org.apache.jena.util.FileUtils;
import org.jpl7.Term;

public class pSPARQL {

	Integer next = 1;
	Integer current = 0;

	Integer nvar = 0;

	List<String> varsOut = new ArrayList<String>();
	List<List<String>> rules = new ArrayList<List<String>>();
	Map<String, String> mapping = new HashMap<String, String>();

	public String readFile(String pathname) throws IOException {

		File file = new File(pathname);
		StringBuilder fileContents = new StringBuilder((int) file.length());
		Scanner scanner = new Scanner(file);
		String lineSeparator = System.getProperty("line.separator");

		try {
			while (scanner.hasNextLine()) {
				fileContents.append(scanner.nextLine() + lineSeparator);
			}
			return fileContents.toString();
		} finally {
			scanner.close();
		}
	}

	public String orderby(Query query) {

		String orderby = "[";
		List<SortCondition> lsc = query.getOrderBy();
		for (SortCondition l : lsc) {
			Integer dir = l.getDirection();
			Var e = l.getExpression().asVar();
			String variable = "A" + nvar;
			nvar++;
			String pt = STermtoPTerm(e.asNode());
			if (dir == -1) {
				orderby = orderby + "desc(" + pt + "),";
			} else {
				orderby = orderby + "asc(" + pt + "),";
			}
		}
		orderby = orderby.substring(0, orderby.length() - 1);
		orderby = orderby + "]";
		return orderby;
	}

	public List<List<String>> SPARQLtoProlog(String queryString, Integer step) {

		final Query query = QueryFactory.create(queryString);
		if (

		query.isConstructType() || query.isDescribeType() || !query.getGraphURIs().isEmpty()
				|| !query.getNamedGraphURIs().isEmpty())

		{
			System.out.println("SPARQL expression not supported");

		}

		else if (query.hasAggregators()) {

			rules.add(current, new ArrayList());
			String head;
			List<String> varsSub = query.getResultVars();

			if (varsSub.isEmpty()) {
				if (current == 0 && step == 0) {
					head = "p";
				} else {
					head = "p" + current + "_" + step;
				}
				head = head + "('" + query.toString().substring(query.toString().indexOf("SELECT")).replace("\n", " ")
						.replace("\r", " ").replaceAll("\\s+", " ") + "')";

			} else {
				if (current == 0 && step == 0) {
					head = "p" + "(";
				} else {
					head = "p" + current + "_" + step + "(";
				}

				head = head + "'" + query.toString().substring(query.toString().indexOf("SELECT")).replace("\n", " ")
						.replace("\r", " ").replaceAll("\\s+", " ") + "'" + ",";

				for (String v : varsSub) {
					head = head + v.toUpperCase() + ",";
				}

				head = head.substring(0, head.length() - 1);
				head = head + ")";
			}

			// FOR ORDER_BY, LIMIT, DISTINCT
			String head_goal = "(";
			for (String v : varsSub) {
				head_goal = head_goal + v.toUpperCase() + ",";
			}
			head_goal = head_goal.substring(0, head_goal.length() - 1);
			head_goal = head_goal + ")";

			if (query.hasOrderBy()) {
				rules.get(current).add("goal" + head_goal);
				String call_goal = head;
				String orderby = orderby(query);
				call_goal = "order_by(" + orderby + "," + call_goal + ")";
				rules.get(current).add(call_goal);
				current = next;
				next++;
				rules.add(current, new ArrayList());
			}
			if (query.hasLimit()) {
				rules.get(current).add("goal" + head_goal);
				String call_goal = head;
				call_goal = "limit(" + query.getLimit() + "," + call_goal + ")";
				rules.get(current).add(call_goal);
				current = next;
				next++;
				rules.add(current, new ArrayList());
			}
			if (query.isDistinct()) {
				rules.get(current).add("goal" + head_goal);
				String call_goal = head;
				call_goal = "distinct(" + call_goal + ")";
				rules.get(current).add(call_goal);
				current = next;
				next++;
				rules.add(current, new ArrayList());
			}
			

			rules.get(current).add(head);

			// HAVING

			List<Expr> le = query.getHavingExprs();

			Stack<String> pt = new Stack<String>();

			for (Expr e : le) {

				pt = SExprtoPTerm(e, null, query, step);
				for (String c : pt) {
					rules.get(current).add(c);
				}

			}

			// AGGREGATION RULES

			Map<Var, Expr> as = query.getProject().getExprs();

			Stack<String> ph = new Stack<String>();
			for (Entry<Var, Expr> ks : as.entrySet()) {
				varsOut.clear();
				Expr e = new E_Equals(new ExprVar(ks.getKey().getName()), ks.getValue());
				ph = SExprtoPTerm(e, null, query, step);
				for (String c : ph) {
					rules.get(current).add(c);
				}

			}
		} else {
			rules.add(current, new ArrayList());
			List<String> varsSub = query.getResultVars();
			String head;
			if (varsSub.isEmpty()) {
				if (current == 0 && step == 0) {
					head = "p";
				} else {
					head = "p" + current + "_" + step;
				}

				head = head + "('" + query.toString().substring(query.toString().indexOf("SELECT")).replace("\n", " ")
						.replace("\r", " ").replaceAll("\\s+", " ") + "')";

			} else {
				if (current == 0 && step == 0) {
					head = "p" + "(";
				} else {
					head = "p" + current + "_" + step + "(";
				}

				head = head + "'" + query.toString().substring(query.toString().indexOf("SELECT")).replace("\n", " ")
						.replace("\r", " ").replaceAll("\\s+", " ") + "'" + ",";

				for (String v : varsSub) {
					head = head + v.toUpperCase() + ",";
				}

				head = head.substring(0, head.length() - 1);
				head = head + ")";
			}

			// FOR ORDER_BY, LIMIT, DISTINCT
			String head_goal = "(";
			for (String v : varsSub) {
				head_goal = head_goal + v.toUpperCase() + ",";
			}
			head_goal = head_goal.substring(0, head_goal.length() - 1);
			head_goal = head_goal + ")";

			
			if (query.hasOrderBy()) {
				rules.get(current).add("goal" + head_goal);
				String call_goal = head;
				String orderby = orderby(query);
				call_goal = "order_by(" + orderby + "," + call_goal + ")";
				rules.get(current).add(call_goal);
				current = next;
				next++;
				rules.add(current, new ArrayList());
			}
			if (query.hasLimit()) {
				rules.get(current).add("goal" + head_goal);
				String call_goal = head;
				call_goal = "limit(" + query.getLimit() + "," + call_goal + ")";
				rules.get(current).add(call_goal);
				current = next;
				next++;
				rules.add(current, new ArrayList());
			}
			if (query.isDistinct()) {
				rules.get(current).add("goal" + head_goal);
				String call_goal = head;
				call_goal = "distinct(" + call_goal + ")";
				rules.get(current).add(call_goal);
				current = next;
				next++;
				rules.add(current, new ArrayList());
			}
			
			//

			rules.get(current).add(head);

			Element e = query.getQueryPattern();

			if (e instanceof ElementGroup) {
				elementGroup((ElementGroup) e, step);
			} else {
				elementSubQuery((ElementSubQuery) e, step);
			}
		}

		return rules;
	}

	public void aggregation(Query query, Var vks, Expr eks, Integer step) {

		Integer tmp = current;
		current = next;
		next++;
		rules.add(current, new ArrayList());
		List<String> varsGroup = new ArrayList();
		for (Var vn : query.getGroupBy().getVars()) {
			varsGroup.add(vn.getName().toUpperCase());
		}

		varsOut.clear();

		// ADDING GROUP BY
		for (String v : varsGroup) {
			varsOut.add(v);
		}

		// ADDING RESULT OF AGG

		if (((ExprAggregator) eks).getAggregator().toString().startsWith("count")) {

		} else {

			Set<Var> varsAgg = ((ExprAggregator) eks).getAggregator().getExprList().get(0).getVarsMentioned();
			for (Var v : varsAgg) {
				varsOut.add(v.getName().toUpperCase());
			}
		}

		String head;

		if (varsOut.isEmpty()) {
			if (current == 0 && step == 0) {
				head = "p";
			} else {
				head = "p" + current + "_" + step;
			}

			head = head + "(" + "'GROUPBY','"
					+ query.getQueryPattern().toString().replace("\n", " ").replace("\r", " ").replaceAll("\\s+", " ")
					+ "')";

		} else {
			if (current == 0 && step == 0) {
				head = "p" + "(";
			} else {
				head = "p" + current + "_" + step + "(";
			}
			String namehead = "";

			for (String v : varsOut) {
				namehead = namehead + " ?" + v.toLowerCase();
			}
			namehead = namehead + " in ";
			head = head + "'GROUPBY'," + "'" + namehead
					+ query.getQueryPattern().toString().replace("\n", " ").replace("\r", " ").replaceAll("\\s+", " ")
					+ "'" + ",";

			if (((ExprAggregator) eks).getAggregator().toString().startsWith("count")) {

			} else {

				for (String v : varsOut) {
					head = head + v.toUpperCase() + ",";
				}

			}

			head = head.substring(0, head.length() - 1);
			head = head + ")";
		}

		rules.get(current).add(head);
		Element e = query.getQueryPattern();

		if (e instanceof ElementGroup) {
			elementGroup((ElementGroup) e, step);
		} else {
			elementSubQuery((ElementSubQuery) e, step);
		}

		if (((ExprAggregator) eks).getAggregator().toString().startsWith("count"))

		{
			String opAgg = "findall";

			if (!query.getGroupBy().isEmpty()) {
				opAgg = "bagof";
			}
			String variable = "A" + nvar;
			nvar++;
			String resBag = "A" + nvar;
			nvar++;
			String sparql_name = ((ExprAggregator) eks).getAggregator().getClass().getSimpleName();
			String prolog_name = callAggregator(sparql_name, resBag, vks.getVarName().toUpperCase());
			rules.get(tmp).add(opAgg + "(" + variable + "," + "(" + head + ")," + resBag + ")," + prolog_name);

		} else {

			String gb = "";
			String opAgg = "findall";
			if (!query.getGroupBy().isEmpty()) {
				opAgg = "bagof";
			}
			String variable = "A" + nvar;
			nvar++;
			String variable2 = "A" + nvar;
			nvar++;
			String variable3 = "A" + nvar;
			nvar++;
			String resBag = "A" + nvar;
			nvar++;

			mapping.clear();

			nvar++;
			List<String> ss = SExprtoPTerm(((ExprAggregator) eks).getAggregator().getExprList().get(0),
					NodeFactory.createVariable(variable), query, step);

			String sparql_name = ((ExprAggregator) eks).getAggregator().getClass().getSimpleName();
			String prolog_name = callAggregator(sparql_name, resBag, variable3);

			for (Var vm : ((ExprAggregator) eks).getAggregator().getExprList().get(0).getVarsMentioned()) {
				String map = mapping.get(vm.getName().replace('?', ' ').toUpperCase());
				gb = vm.getName().replace('?', ' ').toUpperCase() + "^" + map + "^" + gb;
			}

			String findall1 = "";
			if (opAgg.equals("findall")) {
				findall1 = opAgg + "(" + variable2 + "," + "(" + variable2 + "^^" + "_" + "=" + variable + "," + head;
			} else {

				findall1 = opAgg + "(" + variable2 + "," + gb + variable + "^" + "(" + variable2 + "^^" + "_" + "="
						+ variable + "," + head;
			}

			for (int i = 0; i < ss.size(); i++) {
				findall1 = findall1 + "," + ss.get(i);
			}

			String findall = findall1 + ")," + resBag + ")," + prolog_name;
			rules.get(tmp).add(findall);
			rules.get(tmp).add(variable3 + "=" + vks.getVarName().toUpperCase());
		}
		current = tmp;
	}

	public String callAggregator(String sp, String resBag, String Result) {

		String nresult = "A" + nvar;
		nvar++;
		if (sp.equals("AggCountVar"))
			return "proper_length" + "(" + resBag + "," + nresult + ")," + nresult + "^^_=" + Result;
		else if (sp.equals("AggCount"))
			return "proper_length" + "(" + resBag + "," + nresult + ")," + nresult + "^^_=" + Result;
		else if (sp.equals("AggSum"))
			return "sum_list" + "(" + resBag + "," + nresult + ")," + nresult + "^^_=" + Result;
		else if (sp.equals("AggMax"))
			return "max_member" + "(" + nresult + "," + resBag + ")," + nresult + "^^_=" + Result;
		else if (sp.equals("AggMin"))
			return "min_member" + "(" + nresult + "," + resBag + ")," + nresult + "^^_=" + Result;
		else if (sp.equals("AggAvg")) {
			String sum = "A" + nvar;
			nvar++;
			String count = "A" + nvar;
			nvar++;
			return "sum_list" + "(" + resBag + "," + sum + ")," + "proper_length" + "(" + resBag + "," + count + "),"
					+ nresult + " = " + sum + "/" + count + "," + nresult + "^^_=" + Result;
		} else
			return sp;
	}

	public void elementData(ElementData el, Integer step) {

		for (Var v : el.getVars()) {
			String list = "[";
			for (Binding bd : el.getRows()) {

				String value = STermtoPTerm(bd.get(v));
				list = list + value + ",";

			}
			list = list.substring(0, list.length() - 1);
			list = list + "]";
			String member = "member(" + v.getName().toUpperCase() + "," + list + ")";
			rules.get(current).add(member);
		}
	}

	public void elementExists(ElementExists el, Integer step) {

		Integer tmp = current;
		current = next;
		next++;
		rules.add(current, new ArrayList());
		List<String> inputVars = new ArrayList();
		for (String v : varsOut) {
			inputVars.add(v);
		}
		varsOut.clear();
		Element ex = el.getElement();
		if (ex instanceof ElementPathBlock) {
			elementPathBlock((ElementPathBlock) ex, step);
		} else if (ex instanceof ElementData) {
			elementData((ElementData) ex, step);
		} else if (ex instanceof ElementAssign) {
			elementAssign((ElementAssign) ex, step);
		} else if (ex instanceof ElementExists) {
			elementExists((ElementExists) ex, step);
		} else if (ex instanceof ElementNotExists) {
			elementNotExists((ElementNotExists) ex, step);
		} else if (ex instanceof ElementOptional) {
			elementOptional((ElementOptional) ex, step);
		} else if (ex instanceof ElementMinus) {
			elementMinus((ElementMinus) ex, step);
		} else if (ex instanceof ElementSubQuery) {
			elementSubQuery((ElementSubQuery) ex, step);
		} else if (ex instanceof ElementGroup) {
			elementGroup((ElementGroup) ex, step);
		} else if (ex instanceof ElementFilter) {
			elementFilter((ElementFilter) ex, step);
		} else if (ex instanceof ElementBind) {
			elementBind((ElementBind) ex, step);
		} else {
			System.out.println("SPARQL expression not supported");
			rules.clear();
		}

		String head;

		if (varsOut.isEmpty()) {
			if (current == 0 && step == 0) {
				head = "p";
			} else {
				head = "p" + current + "_" + step;
			}
			head = head + "('EXISTS','" + el.toString().replace("\n", " ").replace("\r", " ").replaceAll("\\s+", " ")
					+ ")'";

		} else {
			if (current == 0 && step == 0) {
				head = "p" + "(";
			} else {
				head = "p" + current + "_" + step + "(";
			}
			head = head + "'EXISTS','" + el.toString().replace("\n", " ").replace("\r", " ").replaceAll("\\s+", " ")
					+ "'" + ",";
			for (String v : varsOut) {
				head = head + v.toUpperCase() + ",";
			}
			head = head.substring(0, head.length() - 1);
			head = head + ")";
		}

		varsOut.addAll(inputVars);
		rules.get(current).add(0, head);
		rules.get(tmp).add(head);
		rules.get(current).add("!");
		current = tmp;

	}

	public void elementAssign(ElementAssign el, Integer step) {

		nvar++;
		List<String> ss = new ArrayList<>(SExprtoPTerm(el.getExpr(), el.getVar().asNode(), null, null));
		for (int i = 0; i < ss.size(); i++) {
			rules.get(current).add(ss.get(i));
		}
	}

	public void elementNotExists(ElementNotExists el, Integer step) {

		Integer tmp = current;
		current = next;
		next++;
		rules.add(current, new ArrayList());
		List<String> inputVars = new ArrayList();
		for (String v : varsOut) {
			inputVars.add(v);
		}
		varsOut.clear();
		Element ex = el.getElement();
		if (ex instanceof ElementPathBlock) {
			elementPathBlock((ElementPathBlock) ex, step);
		} else if (ex instanceof ElementData) {
			elementData((ElementData) ex, step);
		} else if (ex instanceof ElementAssign) {
			elementAssign((ElementAssign) ex, step);
		} else if (ex instanceof ElementExists) {
			elementExists((ElementExists) ex, step);
		} else if (ex instanceof ElementNotExists) {
			elementNotExists((ElementNotExists) ex, step);
		} else if (ex instanceof ElementOptional) {
			elementOptional((ElementOptional) ex, step);
		} else if (ex instanceof ElementMinus) {
			elementMinus((ElementMinus) ex, step);
		} else if (ex instanceof ElementSubQuery) {
			elementSubQuery((ElementSubQuery) ex, step);
		} else if (ex instanceof ElementGroup) {
			elementGroup((ElementGroup) ex, step);
		} else if (ex instanceof ElementFilter) {
			elementFilter((ElementFilter) ex, step);
		} else if (ex instanceof ElementBind) {
			elementBind((ElementBind) ex, step);
		} else {
			System.out.println("SPARQL expression not supported");
			rules.clear();
		}

		String head;
		if (varsOut.isEmpty()) {
			if (current == 0 && step == 0) {
				head = "p";
			} else {
				head = "p" + current + "_" + step;
			}
			head = head + "('EXISTS','" + el.toString().replace("\n", " ").replace("\r", " ").replaceAll("\\s+", " ")
					+ ")'";
		} else {
			if (current == 0 && step == 0) {
				head = "p" + "(";
			} else {
				head = "p" + current + "_" + step + "(";
			}
			head = head + "'EXISTS','" + el.toString().replace("\n", " ").replace("\r", " ").replaceAll("\\s+", " ")
					+ "'" + ",";
			for (String v : varsOut) {
				head = head + v.toUpperCase() + ",";
			}
			head = head.substring(0, head.length() - 1);
			head = head + ")";
		}

		varsOut.addAll(inputVars);
		rules.get(current).add(0, head);
		rules.get(tmp).add("(\\+(" + head + "))");
		rules.get(current).add("!");
		current = tmp;
	}

	public void elementFilter(ElementFilter el, Integer step) {

		if (el.getExpr().getFunction().getFunctionName(null) == "exists") {
			Integer tmp = current;
			current = next;
			next++;
			rules.add(current, new ArrayList());
			List<String> inputVars = new ArrayList();
			for (String v : varsOut) {
				inputVars.add(v);
			}
			varsOut.clear();
			Element ex = ((ExprFunctionOp) el.getExpr().getFunction()).getElement();
			if (ex instanceof ElementPathBlock) {
				elementPathBlock((ElementPathBlock) ex, step);
			} else if (ex instanceof ElementData) {
				elementData((ElementData) ex, step);
			} else if (ex instanceof ElementAssign) {
				elementAssign((ElementAssign) ex, step);
			} else if (ex instanceof ElementExists) {
				elementExists((ElementExists) ex, step);
			} else if (ex instanceof ElementNotExists) {
				elementNotExists((ElementNotExists) ex, step);
			} else if (ex instanceof ElementOptional) {
				elementOptional((ElementOptional) ex, step);
			} else if (ex instanceof ElementMinus) {
				elementMinus((ElementMinus) ex, step);
			} else if (ex instanceof ElementSubQuery) {
				elementSubQuery((ElementSubQuery) ex, step);
			} else if (ex instanceof ElementGroup) {
				elementGroup((ElementGroup) ex, step);
			} else if (ex instanceof ElementFilter) {
				elementFilter((ElementFilter) ex, step);
			} else if (ex instanceof ElementBind) {
				elementBind((ElementBind) ex, step);
			} else {
				System.out.println("SPARQL expression not supported");
				rules.clear();
			}

			String head;

			if (varsOut.isEmpty()) {
				if (current == 0 && step == 0) {
					head = "p";
				} else {
					head = "p" + current + "_" + step;
				}
				head = head + "('EXISTS','"
						+ el.toString().replace("\n", " ").replace("\r", " ").replaceAll("\\s+", " ") + ")'";

			} else {
				if (current == 0 && step == 0) {
					head = "p" + "(";
				} else {
					head = "p" + current + "_" + step + "(";
				}
				head = head + "'EXISTS','" + el.toString().replace("\n", " ").replace("\r", " ").replaceAll("\\s+", " ")
						+ "'" + ",";
				for (String v : varsOut) {
					head = head + v.toUpperCase() + ",";
				}
				head = head.substring(0, head.length() - 1);
				head = head + ")";
			}

			varsOut.addAll(inputVars);
			rules.get(current).add(0, head);
			rules.get(tmp).add(head);
			rules.get(current).add("!");
			current = tmp;

		} else

		if (el.getExpr().getFunction().getFunctionName(null) == "notexists") {
			Integer tmp = current;
			current = next;
			next++;
			rules.add(current, new ArrayList());
			List<String> inputVars = new ArrayList();
			for (String v : varsOut) {
				inputVars.add(v);
			}
			varsOut.clear();
			Element ex = ((ExprFunctionOp) el.getExpr().getFunction()).getElement();
			if (ex instanceof ElementPathBlock) {
				elementPathBlock((ElementPathBlock) ex, step);
			} else if (ex instanceof ElementData) {
				elementData((ElementData) ex, step);
			} else if (ex instanceof ElementAssign) {
				elementAssign((ElementAssign) ex, step);
			} else if (ex instanceof ElementExists) {
				elementExists((ElementExists) ex, step);
			} else if (ex instanceof ElementNotExists) {
				elementNotExists((ElementNotExists) ex, step);
			} else if (ex instanceof ElementOptional) {
				elementOptional((ElementOptional) ex, step);
			} else if (ex instanceof ElementMinus) {
				elementMinus((ElementMinus) ex, step);
			} else if (ex instanceof ElementSubQuery) {
				elementSubQuery((ElementSubQuery) ex, step);
			} else if (ex instanceof ElementGroup) {
				elementGroup((ElementGroup) ex, step);
			} else if (ex instanceof ElementFilter) {
				elementFilter((ElementFilter) ex, step);
			} else if (ex instanceof ElementBind) {
				elementBind((ElementBind) ex, step);
			} else {
				System.out.println("SPARQL expression not supported");
				rules.clear();
			}

			String head;
			if (varsOut.isEmpty()) {
				if (current == 0 && step == 0) {
					head = "p";
				} else {
					head = "p" + current + "_" + step;
				}
				head = head + "('EXISTS','"
						+ el.toString().replace("\n", " ").replace("\r", " ").replaceAll("\\s+", " ") + ")'";
			} else {
				if (current == 0 && step == 0) {
					head = "p" + "(";
				} else {
					head = "p" + current + "_" + step + "(";
				}
				head = head + "'EXISTS','" + el.toString().replace("\n", " ").replace("\r", " ").replaceAll("\\s+", " ")
						+ "'" + ",";
				for (String v : varsOut) {
					head = head + v.toUpperCase() + ",";
				}
				head = head.substring(0, head.length() - 1);
				head = head + ")";
			}

			varsOut.addAll(inputVars);
			rules.get(current).add(0, head);
			rules.get(tmp).add("(\\+(" + head + "))");
			rules.get(current).add("!");
			current = tmp;

		} else {
			List<String> ss = new ArrayList<>(SExprtoPTerm(el.getExpr(), null, null, null));
			for (int i = 0; i < ss.size(); i++) {
				rules.get(current).add(ss.get(i));
			}
		}
	}

	public void elementBind(ElementBind el, Integer step) {
		nvar++;
		List<String> ss = new ArrayList<>(SExprtoPTerm(el.getExpr(), el.getVar().asNode(), null, null));
		for (int i = 0; i < ss.size(); i++) {
			rules.get(current).add(ss.get(i));
		}
	}

	public void elementPathBlock(ElementPathBlock el, Integer step) {

		List<TriplePath> lp = el.getPattern().getList();
		for (TriplePath p : lp) {

			if (!p.getSubject().isConcrete() && !varsOut.contains(STermtoPTerm(p.getSubject()))) {
				varsOut.add(STermtoPTerm(p.getSubject()));
			}
			if (!p.getPredicate().isConcrete() && !varsOut.contains(STermtoPTerm(p.getPredicate()))) {
				varsOut.add(STermtoPTerm(p.getPredicate()));
			}

			if (!p.getObject().isConcrete() && !varsOut.contains(STermtoPTerm(p.getObject()))) {
				varsOut.add(STermtoPTerm(p.getObject()));
			}

			String rule = "rdf(" + STermtoPTerm(p.getSubject()) + "," + STermtoPTerm(p.getPredicate()) + ","
					+ STermtoPTerm(p.getObject()) + ")";
			List<String> l = rules.get(current);
			l.add(rule);
		}
	};

	public void elementUnion(ElementUnion el, Integer step) {

		String union = "(";
		for (Element e : el.getElements()) {
			Integer tmp = current;
			current = next;
			next++;
			rules.add(current, new ArrayList());
			List<String> inputVars = new ArrayList();

			for (String v : varsOut) {
				inputVars.add(v);
			}
			varsOut.clear();
			if (e instanceof ElementPathBlock) {
				elementPathBlock((ElementPathBlock) e, step);
			} else if (e instanceof ElementData) {
				elementData((ElementData) e, step);
			} else if (e instanceof ElementAssign) {
				elementAssign((ElementAssign) e, step);
			} else if (e instanceof ElementExists) {
				elementExists((ElementExists) e, step);
			} else if (e instanceof ElementNotExists) {
				elementNotExists((ElementNotExists) e, step);
			} else if (e instanceof ElementOptional) {
				elementOptional((ElementOptional) e, step);
			} else if (e instanceof ElementMinus) {
				elementMinus((ElementMinus) e, step);
			} else if (e instanceof ElementSubQuery) {
				elementSubQuery((ElementSubQuery) e, step);
			} else if (e instanceof ElementGroup) {
				elementGroup((ElementGroup) e, step);
			} else if (e instanceof ElementFilter) {
				elementFilter((ElementFilter) e, step);
			} else if (e instanceof ElementBind) {
				elementBind((ElementBind) e, step);
			} else if (e instanceof ElementUnion) {
				elementUnion((ElementUnion) e, step);
			} else {
				System.out.println("SPARQL expression not supported");
				rules.clear();
			}

			String head;

			if (varsOut.isEmpty()) {
				if (current == 0 && step == 0) {
					head = "p";
				} else {
					head = "p" + current + "_" + step;
				}

				head = head + "('" + el.toString().replace("\n", " ").replace("\r", " ").replaceAll("\\s+", " ") + ")'";

			} else {
				if (current == 0 && step == 0) {
					head = "p" + "(";
				} else {
					head = "p" + current + "_" + step + "(";
				}
				head = head + "'" + el.toString().replace("\n", " ").replace("\r", " ").replaceAll("\\s+", " ") + "'"
						+ ",";
				for (String v : varsOut) {
					head = head + v.toUpperCase() + ",";
				}
				head = head.substring(0, head.length() - 1);
				head = head + ")";
			}

			rules.get(current).add(0, head);
			union = union + head + ";";
			varsOut.addAll(inputVars);
			current = tmp;
		}

		union = union.substring(0, union.length() - 1);
		union = union + ")";
		rules.get(current).add(union);
	}

	public void elementGroup(ElementGroup el, Integer step) {

		for (Element e : el.getElements()) {
			if (e instanceof ElementPathBlock) {
				elementPathBlock((ElementPathBlock) e, step);
			} else if (e instanceof ElementData) {
				elementData((ElementData) e, step);
			} else if (e instanceof ElementAssign) {
				elementAssign((ElementAssign) e, step);
			} else if (e instanceof ElementExists) {
				elementExists((ElementExists) e, step);
			} else if (e instanceof ElementNotExists) {
				elementNotExists((ElementNotExists) e, step);
			} else if (e instanceof ElementOptional) {
				elementOptional((ElementOptional) e, step);
			} else if (e instanceof ElementMinus) {
				elementMinus((ElementMinus) e, step);
			} else if (e instanceof ElementSubQuery) {
				elementSubQuery((ElementSubQuery) e, step);
			} else if (e instanceof ElementUnion) {
				elementUnion((ElementUnion) e, step);
			} else if (e instanceof ElementFilter) {
				elementFilter((ElementFilter) e, step);
			} else if (e instanceof ElementBind) {
				elementBind((ElementBind) e, step);
			} else if (e instanceof ElementGroup) {
				elementGroup((ElementGroup) e, step);
			} else {
				System.out.println("SPARQL expression not supported");
				rules.clear();
			}
		}

	}

	public void elementMinus(ElementMinus el, Integer step) {
		Element e = el.getMinusElement();
		Integer tmp = current;
		current = next;
		next++;
		rules.add(current, new ArrayList());
		List<String> inputVars = new ArrayList();
		for (String v : varsOut) {
			inputVars.add(v);
		}
		varsOut.clear();
		if (e instanceof ElementPathBlock) {
			elementPathBlock((ElementPathBlock) e, step);
		} else if (e instanceof ElementData) {
			elementData((ElementData) e, step);
		} else if (e instanceof ElementAssign) {
			elementAssign((ElementAssign) e, step);
		} else if (e instanceof ElementExists) {
			elementExists((ElementExists) e, step);
		} else if (e instanceof ElementNotExists) {
			elementNotExists((ElementNotExists) e, step);
		} else if (e instanceof ElementOptional) {
			elementOptional((ElementOptional) e, step);
		} else if (e instanceof ElementMinus) {
			elementMinus((ElementMinus) e, step);
		} else if (e instanceof ElementSubQuery) {
			elementSubQuery((ElementSubQuery) e, step);
		} else if (e instanceof ElementGroup) {
			elementGroup((ElementGroup) e, step);
		} else if (e instanceof ElementFilter) {
			elementFilter((ElementFilter) e, step);
		} else if (e instanceof ElementBind) {
			elementBind((ElementBind) e, step);
		} else {
			System.out.println("SPARQL expression not supported");
			rules.clear();
		}

		String head;

		if (varsOut.isEmpty()) {
			/* GOAL */
			if (current == 0 && step == 0) {
				head = "p";
			} else {
				head = "p" + current + "_" + step;
			}

			head = head + "('MINUS','" + el.toString().replace("\n", " ").replace("\r", " ").replaceAll("\\s+", " ")
					+ ")'";

		} else {
			/* GOAL */
			if (current == 0 && step == 0) {
				head = "p" + "(";
			} else {
				head = "p" + current + "_" + step + "(";
			}

			head = head + "'MINUS','" + el.toString().replace("\n", " ").replace("\r", " ").replaceAll("\\s+", " ")
					+ "'" + ",";

			for (String v : varsOut) {
				head = head + v.toUpperCase() + ",";
			}
			head = head.substring(0, head.length() - 1);
			head = head + ")";
		}

		rules.get(current).add(0, head);
		rules.get(tmp).add("\\+(" + head + ")");
		varsOut.addAll(inputVars);
		current = tmp;

	}

	public void elementOptional(ElementOptional el, Integer step) {

		Element e = el.getOptionalElement();

		Integer tmp = current;
		current = next;
		next++;
		rules.add(current, new ArrayList());
		List<String> inputVars = new ArrayList();
		for (String v : varsOut) {
			inputVars.add(v);
		}

		varsOut.clear();

		if (e instanceof ElementPathBlock) {
			elementPathBlock((ElementPathBlock) e, step);
		} else if (e instanceof ElementData) {
			elementData((ElementData) e, step);
		} else if (e instanceof ElementAssign) {
			elementAssign((ElementAssign) e, step);
		} else if (e instanceof ElementExists) {
			elementExists((ElementExists) e, step);
		} else if (e instanceof ElementNotExists) {
			elementNotExists((ElementNotExists) e, step);
		} else if (e instanceof ElementOptional) {
			elementOptional((ElementOptional) e, step);
		} else if (e instanceof ElementMinus) {
			elementMinus((ElementMinus) e, step);
		} else if (e instanceof ElementSubQuery) {
			elementSubQuery((ElementSubQuery) e, step);
		} else if (e instanceof ElementGroup) {
			elementGroup((ElementGroup) e, step);
		} else if (e instanceof ElementFilter) {
			elementFilter((ElementFilter) e, step);
		} else if (e instanceof ElementBind) {
			elementBind((ElementBind) e, step);
		} else {
			System.out.println("SPARQL expression not supported");
			rules.clear();
		}

		String head;

		if (varsOut.isEmpty()) {
			if (current == 0 && step == 0) {
				head = "p";
			} else {
				head = "p" + current + "_" + step;
			}

			head = head + "('OPTIONAL','" + el.toString().replace("\n", " ").replace("\r", " ").replaceAll("\\s+", " ")
					+ ")'";

		} else {
			if (current == 0 && step == 0) {
				head = "p" + "(";
			} else {
				head = "p" + current + "_" + step + "(";
			}

			head = head + "'OPTIONAL','" + el.toString().replace("\n", " ").replace("\r", " ").replaceAll("\\s+", " ")
					+ "'" + ",";

			for (String v : varsOut) {
				head = head + v.toUpperCase() + ",";
			}
			head = head.substring(0, head.length() - 1);
			head = head + ")";
		}

		rules.get(current).add(0, head);
		rules.get(tmp).add("(" + head + ";" + "\\+(" + head + "))");
		varsOut.addAll(inputVars);
		current = tmp;

	};

	public void elementSubQuery(ElementSubQuery el, Integer step) {

		Query query = el.getQuery();

		if (

		query.isConstructType() ||

				query.isDescribeType() ||

				!query.getGraphURIs().isEmpty() ||

				!query.getNamedGraphURIs().isEmpty())

		{
			System.out.println("SPARQL expression not supported");

		}

		else if (query.hasAggregators()) {

			Integer tmp = current;
			current = next;
			next++;
			rules.add(current, new ArrayList());

			List<String> varsSub = query.getResultVars();

			String head;

			if (varsSub.isEmpty()) {
				if (current == 0 && step == 0) {
					head = "p";
				} else {
					head = "p" + current + "_" + step;
				}

				head = head + "('" + el.toString().replace("\n", " ").replace("\r", " ").replaceAll("\\s+", " ") + ")'";

			} else {
				if (current == 0 && step == 0) {
					head = "p" + "(";
				} else {
					head = "p" + current + "_" + step + "(";
				}

				head = head + "'" + el.toString().replace("\n", " ").replace("\r", " ").replaceAll("\\s+", " ") + "'"
						+ ",";

				for (String v : varsSub) {
					head = head + v.toUpperCase() + ",";
				}

				head = head.substring(0, head.length() - 1);
				head = head + ")";
			}

			rules.get(current).add(0, head);
			rules.get(tmp).add(head);

			// HAVING

			List<Expr> le = query.getHavingExprs();
			Stack<String> pt = new Stack<String>();

			for (Expr e : le) {

				pt = SExprtoPTerm(e, null, query, step);
				for (String c : pt) {
					rules.get(current).add(c);
				}

			}

			// AGGREGATION RULES

			Map<Var, Expr> as = query.getProject().getExprs();
			List<String> inputVars = new ArrayList();
			for (String v : varsOut) {
				inputVars.add(v);
			}

			Stack<String> ph = new Stack<String>();
			for (Entry<Var, Expr> ks : as.entrySet()) {
				varsOut.clear();
				Expr e = new E_Equals(new ExprVar(ks.getKey().getName()), ks.getValue());
				ph = SExprtoPTerm(e, null, query, step);
				for (String c : ph) {
					rules.get(current).add(c);
				}

			}

			varsOut.clear();
			varsOut.addAll(inputVars);
			for (String v : varsSub) {
				varsOut.add(v.toUpperCase());
			}
			current = tmp;

		}

		else

		{
			Integer tmp = current;
			current = next;
			next++;
			rules.add(current, new ArrayList());

			List<String> varsSub = el.getQuery().getResultVars();

			String head;

			if (varsSub.isEmpty()) {
				if (current == 0 && step == 0) {
					head = "p";
				} else {
					head = "p" + current + "_" + step;
				}

				head = head + "('" + el.toString().replace("\n", " ").replace("\r", " ").replaceAll("\\s+", " ") + ")'";

			} else {
				if (current == 0 && step == 0) {
					head = "p" + "(";
				} else {
					head = "p" + current + "_" + step + "(";
				}

				head = head + "'" + el.toString().replace("\n", " ").replace("\r", " ").replaceAll("\\s+", " ") + "'"
						+ ",";

				for (String v : varsSub) {
					head = head + v.toUpperCase() + ",";
				}
				head = head.substring(0, head.length() - 1);
				head = head + ")";
			}

			rules.get(current).add(head);

			// FOR ORDER_BY, LIMIT, DISTINCT
			String head_goal = "(";
			for (String v : varsSub) {
				head_goal = head_goal + v.toUpperCase() + ",";
			}
			head_goal = head_goal.substring(0, head_goal.length() - 1);
			head_goal = head_goal + ")";

			if (query.hasOrderBy()) {
				rules.get(current).add("goal" + head_goal);
				String call_goal = head;
				String orderby = orderby(query);
				call_goal = "order_by(" + orderby + "," + call_goal + ")";
				rules.get(current).add(call_goal);
				current = next;
				next++;
				rules.add(current, new ArrayList());
			}
			if (query.hasLimit()) {
				rules.get(current).add("goal" + head_goal);
				String call_goal = head;
				call_goal = "limit(" + query.getLimit() + "," + call_goal + ")";
				rules.get(current).add(call_goal);
				current = next;
				next++;
				rules.add(current, new ArrayList());
			}
			if (query.isDistinct()) {
				rules.get(current).add("goal" + head_goal);
				String call_goal = head;
				call_goal = "distinct(" + call_goal + ")";
				rules.get(current).add(call_goal);
				current = next;
				next++;
				rules.add(current, new ArrayList());
			}
			

			rules.get(tmp).add(head);

			Map<Var, Expr> as = el.getQuery().getProject().getExprs();

			for (Var v : as.keySet()) {
				Stack<String> ss = SExprtoPTerm(as.get(v).getFunction(), v, null, null);
				for (String s : ss) {
					rules.get(current).add(s);

				}
			}

			List<String> inputVars = new ArrayList();

			for (String v : varsOut) {
				inputVars.add(v);
			}

			Element e = el.getQuery().getQueryPattern();

			if (e instanceof ElementPathBlock) {
				elementPathBlock((ElementPathBlock) e, step);
			} else if (e instanceof ElementData) {
				elementData((ElementData) e, step);
			} else if (e instanceof ElementAssign) {
				elementAssign((ElementAssign) e, step);
			} else if (e instanceof ElementExists) {
				elementExists((ElementExists) e, step);
			} else if (e instanceof ElementNotExists) {
				elementNotExists((ElementNotExists) e, step);
			} else if (e instanceof ElementOptional) {
				elementOptional((ElementOptional) e, step);
			} else if (e instanceof ElementMinus) {
				elementMinus((ElementMinus) e, step);
			} else if (e instanceof ElementSubQuery) {
				elementSubQuery((ElementSubQuery) e, step);
			} else if (e instanceof ElementGroup) {
				elementGroup((ElementGroup) e, step);
			} else if (e instanceof ElementFilter) {
				elementFilter((ElementFilter) e, step);
			} else if (e instanceof ElementBind) {
				elementBind((ElementBind) e, step);
			} else {
				System.out.println("SPARQL expression not supported");
				rules.clear();
			}

			varsOut.clear();

			varsOut.addAll(inputVars);

			for (String v : varsSub) {
				varsOut.add(v.toUpperCase());
			}

			current = tmp;
		}

	}

	public static String STermtoPTerm(Node st) {
		String pt = "";

		if (st.isVariable()) {
			if (st.getName().startsWith("?")) {
				pt = "X" + st.getName().substring(1);
			} else
				pt = st.toString().replace('?', ' ').replaceAll("\\s", "").toUpperCase();

		} else if (st.isURI()) {
			pt = "'" + st.toString() + "'";
		}

		else if (st.isLiteral()) {
			if (st.getLiteralDatatypeURI() == null)

			{
				// SYMBOLIC CONSTANTS
				if (st.toString().startsWith("\"#")) {
					pt = st.toString().replaceAll("\"", "");
				} else {
					pt = st.toString(); // DUDA + "^^" + "'http://www.w3.org/2001/XMLSchema#string'";
				}
			}

			else {
				pt = "'^^'('" + st.getLiteralValue() + "'" + ",'" + st.getLiteralDatatypeURI() + "')";
			}

		}

		return pt;
	}

	public Stack<String> SExprtoPTerm(Expr st, Node var, Query query, Integer step) {

		Stack<String> pt = new Stack<String>();
		if (st instanceof ExprAggregator) {
			aggregation(query, (Var) Var.alloc(var.getName()), st, step);
		}

		else {
			// IF VAR is NULL means that no return has to be provided to the Expr st
			if (var == null) {
				if (st.isVariable()) {
					pt.add(st.toString().replace('?', ' ').replaceAll("\\s", "").toUpperCase());
				} else if (st.isConstant()) {
					// SYMBOLIC CONSTANTS
					if (st.toString().startsWith("\"#")) {
						pt.add(st.toString().replaceAll("\"", ""));
					} else {
						if (st.getConstant().getDatatypeURI() == null)
							// STRING
							pt.add(st.toString());
						else
							pt.add(st.toString() + "^^'" + st.getConstant().getDatatypeURI() + "'");
					}
				} else if (st.isFunction()) {
					if (st.getFunction().getFunctionIRI() == null) {
						// ARITHMETIC FUNCTIONS
						Integer act = nvar;
						nvar++;
						Integer na = nvar;
						List<String> ss = new ArrayList<>(SExprtoPTerm(st.getFunction().getArg(1),
								NodeFactory.createVariable("A" + na), query, step));
						for (int i = 0; i < ss.size(); i++) {
							pt.add(ss.get(i));
						}
						nvar++;
						Integer nb = nvar;
						List<String> ss2 = new ArrayList<>(SExprtoPTerm(st.getFunction().getArg(2),
								NodeFactory.createVariable("B" + nb), query, step));
						for (int i = 0; i < ss2.size(); i++) {
							pt.add(ss2.get(i));
						}
						if (st.getFunction().getOpName().equals("=")) {

							pt.add("A" + na + "=" + "B" + nb);

						} else if (st.getFunction().getOpName().equals("<=")) {

							pt.add("{ A" + na + "=<" + "B" + nb + " }");

						} else if (st.getFunction().getOpName().equals("&&")) {

							pt.add(" A" + na + "=1^^_," + "B" + nb + "=1^^_ ");

						} else if (st.getFunction().getOpName().equals("||")) {

							pt.add("( A" + na + "=1^^_;" + "B" + nb + "=1^^_ )");

						} else {

							pt.add("{ A" + na + st.getFunction().getOpName() + "B" + nb + " }");
						}

						nvar++;
					} else {
						List<Expr> args = st.getFunction().getArgs();
						List<String> varsh = new ArrayList();
						String argsvars = "";
						Integer act = nvar;
						for (int i = 0; i < args.size(); i++) {
							varsh.add("A" + i + "_" + act);
						}
						for (int i = 0; i < args.size(); i++) {
							argsvars = argsvars + "A" + i + "_" + act + ",";
						}
						argsvars = argsvars.substring(0, argsvars.length() - 1);
						for (int i = 0; i < args.size(); i++) {
							nvar++;
							List<String> ss = new ArrayList<>(
									SExprtoPTerm(args.get(i), NodeFactory.createVariable(varsh.get(i)), query, step));
							for (int j = 0; j < ss.size(); j++) {
								pt.add(ss.get(j));
							}
						}
						pt.add("'" + st.getFunction().getFunctionIRI() + "'(" + argsvars + ",VAR" + act + ")");
						nvar++;
					}

				}
			} // IF VAR is not NULL means that return has to be provided to the Expr st
			else

			if (st.isVariable()) {
				pt.add(st.toString().replace('?', ' ').replaceAll("\\s", "").toUpperCase() + "="
						+ var.toString().replace('?', ' ').replaceAll("\\s", "").toUpperCase());

				mapping.put(st.toString().replace('?', ' ').replaceAll("\\s", "").toUpperCase(),
						var.toString().replace('?', ' ').replaceAll("\\s", "").toUpperCase());

			} else if (st.isConstant()) {
				// SYMBOLIC CONSTANTS
				if (st.toString().startsWith("\"#")) {
					pt.add(st.toString().replaceAll("\"", "") + "="
							+ var.toString().replace('?', ' ').replaceAll("\\s", "").toUpperCase());
				} else {
					if (st.getConstant().getDatatypeURI() == null)
						// STRING
						pt.add(st.toString() + "="
								+ var.toString().replace('?', ' ').replaceAll("\\s", "").toUpperCase());
					else
						pt.add(st.toString() + "^^'" + st.getConstant().getDatatypeURI() + "'" + "="
								+ var.toString().replace('?', ' ').replaceAll("\\s", "").toUpperCase());
				}
			} else if (st.isFunction()) {
				if (st.getFunction().getFunctionIRI() == null) {
					if (st.getFunction().getOpName() == null)
					// BUILT-IN
					{
						List<Expr> args = st.getFunction().getArgs();
						List<String> varsh = new ArrayList();
						String argsvars = "";
						Integer act = nvar;
						for (int i = 0; i < args.size(); i++) {
							varsh.add("A" + i + "_" + act);
						}
						for (int i = 0; i < args.size(); i++) {
							argsvars = argsvars + "A" + i + "_" + act + ",";
						}
						argsvars = argsvars.substring(0, argsvars.length() - 1);
						for (int i = 0; i < args.size(); i++) {
							nvar++;
							List<String> ss = new ArrayList<>(
									SExprtoPTerm(args.get(i), NodeFactory.createVariable(varsh.get(i)), query, step));
							for (int j = 0; j < ss.size(); j++) {
								pt.add(ss.get(j));
							}
						}
						pt.add("VAR" + act + "="
								+ var.toString().replace('?', ' ').replaceAll("\\s", "").toUpperCase());
						pt.add(st.getFunction().getFunctionSymbol().getSymbol() + "(" + argsvars + ",VAR" + act + ")");
						nvar++;
					} else {

						nvar++;
						Integer na = nvar;
						List<String> ss = new ArrayList<>(SExprtoPTerm(st.getFunction().getArg(1),
								NodeFactory.createVariable("A" + na), query, step));
						for (int i = 0; i < ss.size(); i++)
							pt.add(ss.get(i));
						nvar++;
						Integer nb = nvar;
						List<String> ss2 = new ArrayList<>(SExprtoPTerm(st.getFunction().getArg(2),
								NodeFactory.createVariable("B" + nb), query, step));
						for (int i = 0; i < ss2.size(); i++)
							pt.add(ss2.get(i));

						String res = var.toString().replace('?', ' ').replaceAll("\\s", "").toUpperCase();

						if (st.getFunction().getOpName().equals("=")) {
							pt.add("( A" + na + "=" + "B" + nb + "->" + res + " = 1^^_" + ";" + res + " = 0^^_)");

						} else if (st.getFunction().getOpName().equals("<=")) {
							pt.add("( {A" + na + "=<" + "B" + nb + "}->" + res + " = 1^^_" + ";" + res + " = 0^^_)");

						} else if (st.getFunction().getOpName().equals(">=")) {
							pt.add("( {A" + na + ">=" + "B" + nb + "}->" + res + " = 1^^_" + ";" + res + " = 0^^_)");

						} else if (st.getFunction().getOpName().equals("<")) {
							pt.add("( {A" + na + "<" + "B" + nb + "}->" + res + " = 1^^_" + ";" + res + " = 0^^_)");

						} else if (st.getFunction().getOpName().equals(">")) {
							pt.add("( {A" + na + ">" + "B" + nb + "}->" + res + " = 1^^_" + ";" + res + " = 0^^_)");
						} else if (st.getFunction().getOpName().equals("&&")) {
							pt.add("( ( (A" + na + "= 1^^_ ," + "B" + nb + " = 1^^_,! )) ->" + res + " = 1^^_ " + ";"
									+ res + " = 0^^_)");
						} else if (st.getFunction().getOpName().equals("||")) {
							pt.add("( (( A" + na + "= 1^^_ ;" + "B" + nb + " = 1^^_,!)) ->" + res + " = 1^^_" + ";"
									+ res + " = 0^^_)");
						} else {
							pt.add("call_function(A" + na + "," + "B" + nb + "," + st.getFunction().getOpName() + ","
									+ res + ")");
						}

						nvar++;
					}
				} else { // SYMBOLIC CONSTANTS
					if (st.getFunction().getFunctionIRI().equals("http://www.lattice.org#APP")) {
						List<Expr> args = st.getFunction().getArgs();
						List<String> varsh = new ArrayList();
						String argsvars = "";
						Integer act = nvar;
						for (int i = 0; i < args.size(); i++) {
							varsh.add("A" + i + "_" + act);
						}
						String operator = args.get(0).getConstant().asUnquotedString();
						for (int i = 1; i < args.size(); i++) {
							argsvars = argsvars + "A" + i + "_" + act + ",";
						}
						argsvars = argsvars.substring(0, argsvars.length() - 1);
						for (int i = 1; i < args.size(); i++) {
							nvar++;
							List<String> ss = new ArrayList<>(
									SExprtoPTerm(args.get(i), NodeFactory.createVariable(varsh.get(i)), query, step));
							for (int j = 0; j < ss.size(); j++) {
								pt.add(ss.get(j));
							}
						}
						pt.add("VAR" + act + "="
								+ var.toString().replace('?', ' ').replaceAll("\\s", "").toUpperCase());
						pt.add("#" + operator + "(" + argsvars + ",VAR" + act + ")");
						nvar++;
					} else {
						List<Expr> args = st.getFunction().getArgs();
						List<String> varsh = new ArrayList();
						String argsvars = "";
						Integer act = nvar;
						for (int i = 0; i < args.size(); i++) {
							varsh.add("A" + i + "_" + act);
						}
						for (int i = 0; i < args.size(); i++) {
							argsvars = argsvars + "A" + i + "_" + act + ",";
						}
						argsvars = argsvars.substring(0, argsvars.length() - 1);
						for (int i = 0; i < args.size(); i++) {
							nvar++;
							List<String> ss = new ArrayList<>(
									SExprtoPTerm(args.get(i), NodeFactory.createVariable(varsh.get(i)), query, step));
							for (int j = 0; j < ss.size(); j++) {
								pt.add(ss.get(j));
							}
						}
						pt.add("VAR" + act + "="
								+ var.toString().replace('?', ' ').replaceAll("\\s", "").toUpperCase());
						pt.add("'" + st.getFunction().getFunctionIRI() + "'(" + argsvars + ",VAR" + act + ")");
						nvar++;
					}
				}

			}
		}

		return pt;
	}

	public String SPARQL(String filei, String queryStr) {

		OntModel model = ModelFactory.createOntologyModel();
		model.read(filei);
		Query query = QueryFactory.create(queryStr);

		if (query.isSelectType()) {
			ResultSet result = (ResultSet) QueryExecutionFactory.create(query, model).execSelect();
			File theDir = new File("tmp-sparql");
			if (!theDir.exists()) {
				theDir.mkdir();
			}
			String fileName = "./tmp-sparql/" + "result.owl";
			File f = new File(fileName);
			FileOutputStream file;
			try {
				file = new FileOutputStream(f);
				ResultSetFormatter.outputAsXML(file, (ResultSet) result);
				try {
					file.close();

				} catch (IOException e) {

					e.printStackTrace();
				}
			} catch (FileNotFoundException e1) {

				e1.printStackTrace();
			}

			String s = "";
			try {
				s = readFile(fileName);
			} catch (IOException e) {

				e.printStackTrace();
			}

			final File[] files = theDir.listFiles();
			for (File g : files)
				g.delete();
			theDir.delete();
			return s;
		} else

		if (query.isConstructType()) {
			Model result = QueryExecutionFactory.create(query, model).execConstruct();
			File theDir = new File("tmp-sparql");
			if (!theDir.exists()) {
				theDir.mkdir();
			}
			String fileName = "./tmp-sparql/" + "result.owl";
			File f = new File(fileName);
			FileOutputStream file;
			try {
				file = new FileOutputStream(f);
				result.write(file, FileUtils.langXMLAbbrev);
				try {
					file.close();

				} catch (IOException e) {

					e.printStackTrace();
				}
			} catch (FileNotFoundException e1) {

				e1.printStackTrace();
			}

			String s = "";
			try {
				s = readFile(fileName);
			} catch (IOException e) {

				e.printStackTrace();
			}
			final File[] files = theDir.listFiles();
			for (File g : files)
				g.delete();
			theDir.delete();
			return s;
		} else

		if (query.isDescribeType()) {
			Model result = QueryExecutionFactory.create(query, model).execDescribe();

			File theDir = new File("tmp-sparql");
			if (!theDir.exists()) {
				theDir.mkdir();
			}
			String fileName = "./tmp-sparql/" + "result.owl";

			File f = new File(fileName);

			FileOutputStream file;

			try {
				file = new FileOutputStream(f);
				result.write(file, FileUtils.langXMLAbbrev);
				try {
					file.close();

				} catch (IOException e) {

					e.printStackTrace();
				}
			} catch (FileNotFoundException e1) {

				e1.printStackTrace();
			}

			String s = "";
			try {
				s = readFile(fileName);
			} catch (IOException e) {

				e.printStackTrace();
			}
			final File[] files = theDir.listFiles();
			for (File g : files)
				g.delete();
			theDir.delete();
			return s;
		} else

		{
			Boolean b = QueryExecutionFactory.create(query, model).execAsk();
			return b.toString();
		}

	};

	public static void main(String[] args) {

		String prog1 = "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>"
				+ "PREFIX rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#>" + "PREFIX f: <http://www.fuzzy.org/>"
				+ "PREFIX sn: <http://www.semanticweb.org/social#>"
				+ "PREFIX apf: <http://jena.hpl.hp.com/ARQ/property#>" + "SELECT ?Ind WHERE {"
				+ "?Ind rdf:type sn:User " + "} ";

		String prog2 = "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>"
				+ "PREFIX rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#>"
				+ "PREFIX sn: <http://www.semanticweb.org/social#>" + "SELECT ?Ind  {"
				+ "?Ind rdf:type sn:User ;  sn:age 51  } ";

		String prog3 = "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>"
				+ "PREFIX rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#>"
				+ "PREFIX sn: <http://www.semanticweb.org/social#>"
				+ "SELECT ?X ?Z  WHERE { ?X rdf:type sn:User . ?X ?Y ?Z }";

		String prog4 = "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>"
				+ "PREFIX rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#>"
				+ "PREFIX sn: <http://www.semanticweb.org/social#>"
				+ "SELECT ?user WHERE {" + "?user rdf:type sn:User }";

		String prog5 = "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>"
				+ "PREFIX rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#>"
				+ "PREFIX sn: <http://www.semanticweb.org/social#>"
				+ "SELECT ?Ind WHERE  {" + "?Ind rdf:type sn:User .  ?Ind sn:age ?Age  . FILTER(?Age > 30) } ";

		String prog6 = "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n"
				+ "PREFIX owl: <http://www.w3.org/2002/07/owl#>\n" + "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>\n"
				+ "PREFIX rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n"
				+ "PREFIX sn: <http://www.semanticweb.org/social#>\n"
				+ "SELECT ?NUSER1 ?NUSER2 WHERE {\n" + "?USER1 sn:name ?NUSER1 . ?USER2 sn:name ?NUSER2 . \n"
				+ "?USER1 sn:age ?AU1 . ?USER2 sn:age ?AU2 . \n " + "FILTER(?AU1 > 40 ).\n" + "FILTER (?AU2 > 50) }\n";

		 
		String prog7 = "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n"
				+ "PREFIX owl: <http://www.w3.org/2002/07/owl#>\n" + "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>\n"
				+ "PREFIX rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n"
				+ "PREFIX sn: <http://www.semanticweb.org/social#>\n"
				+ "SELECT ?USER WHERE {\n" + "?USER sn:age ?AGE .\n" + "FILTER (?AGE > 25) .\n"
				+ "FILTER EXISTS {SELECT ?USER WHERE {\n" + "?USER sn:age ?AGE .\n" + "}}}\n";

		String prog8 = "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n"
				+ "PREFIX owl: <http://www.w3.org/2002/07/owl#>\n" + "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>\n"
				+ "PREFIX rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n"
				+ "PREFIX sn: <http://www.semanticweb.org/social#>\n"
				+ "SELECT ?USER ?EVENT WHERE {\n" + "?USER rdf:type sn:User .\n" + "?USER sn:age ?AGE .\n"
				+ "FILTER (?AGE > 40) .\n" + "?USER sn:attends_to ?EVENT" + "}\n";

		String prog9 = "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>"

				+ "PREFIX rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#>"
				+ "PREFIX sn: <http://www.semanticweb.org/social#>" + "SELECT ?y WHERE "
				+ "{ ?Ind rdf:type sn:User ." + "BIND(?Ind as ?y)} ";

		 
		String prog10 = "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>"

				+ "PREFIX rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#>"
				+ "PREFIX sn: <http://www.semanticweb.org/social#>" + "SELECT ?Ind WHERE "
				+ "{ ?Ind sn:attends_to ?y .  { SELECT ?y1 WHERE {?y1 rdf:type sn:Event  "
				+ ". { SELECT ?y2 WHERE {?y2 rdf:type sn:Event   .  { SELECT ?y3 WHERE {?y3 rdf:type sn:Event }}}} "
				+ "} }}";

		 
		String prog11 = "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>"

				+ "PREFIX rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#>" + "PREFIX f: <http://www.fuzzy.org/>"
				+ "PREFIX sn: <http://www.semanticweb.org/social#>"
				+ "PREFIX apf: <http://jena.hpl.hp.com/ARQ/property#>" + "SELECT ?Ind ?age ?event WHERE "
				+ "{  ?Ind sn:age ?age ." + "OPTIONAL { SELECT ?Ind ?event WHERE { ?Ind sn:attends_to ?event "
				+ "OPTIONAL {?Ind rdf:type sn:User} } } " + "} ";

		// NO SOPORTADA
		String prog12 = "PREFIX rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#>"
				+ "PREFIX f: <http://www.fuzzy.org/>"
				+ "PREFIX sn: <http://www.semanticweb.org/social#>"
				+ "PREFIX apf: <http://jena.hpl.hp.com/ARQ/property#>" + "SELECT ?name "
				+ "FROM NAMED   <http://www.semanticweb.org/ontologies/2011/7/miscojones.owl#> "
				+ "WHERE   { ?Ind sn:name ?name }" + "ORDER BY ?Ind";

		 
		String prog13 = "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n"
				+ "PREFIX owl: <http://www.w3.org/2002/07/owl#>\n" + "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>\n"
				+ "PREFIX rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n"
				+ "PREFIX sn: <http://www.semanticweb.org/social#>\n"
				+ "SELECT ?USER WHERE {\n" + "?USER sn:age ?AGE .\n" + "FILTER (?AGE > 25) .\n"
				+ "FILTER NOT EXISTS {SELECT ?USER WHERE {\n" + "?USER sn:attends_to ?Event \n" + "}\n" + "}}\n";

		String prog14 = "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>"

				+ "PREFIX rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#>"
				+ "PREFIX sn: <http://www.semanticweb.org/social#>" + "SELECT ?Ind WHERE "
				+ "{ ?Ind rdf:type sn:User ."
				+ "OPTIONAL { ?Ind2 rdf:type sn:User . OPTIONAL {?Ind3 rdf:type sn:User } } }";

		String prog15 = "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>"
				+ "PREFIX rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#>"
				+ "PREFIX sn: <http://www.semanticweb.org/social#>"
				+ "SELECT ?y1 ?y2 ?y3 WHERE { " + " { ?y1 rdf:type sn:Event }  " + "UNION"
				+ "{ {?y2 rdf:type sn:Event } UNION { ?y3 rdf:type sn:Event } } }";

		// NO SOPORTADA
		String prog16 = "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>"
				+ "PREFIX rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#>"
				+ "PREFIX sn: <http://www.semanticweb.org/social#>" + "SELECT ?x WHERE "
				+ "{ ?x sn:age ?y ." + "VALUES ?y {51} }";

		String prog17 = "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>"
				+ "PREFIX rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#>"
				+ "PREFIX sn: <http://www.semanticweb.org/social#>" + "SELECT ?Ind WHERE "
				+ "{ ?Ind rdf:type sn:User ." + "  { ?Ind sn:attends_to ?event2 }  " + "} ";

		String prog18 = "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>"
				+ "PREFIX rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#>"
				+ "PREFIX sn: <http://www.semanticweb.org/social#>" + "SELECT ?Ind WHERE "
				+ "{ ?Ind rdf:type sn:User . MINUS { ?Ind sn:attends_to ?Event } }";

		String prog19 = "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>"
				+ "PREFIX rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#>"
				+ "PREFIX sn: <http://www.semanticweb.org/social#>" + "SELECT ?Ind WHERE "
				+ "{ ?Ind rdf:type sn:User . MINUS { ?Ind sn:attends_to ?Event } }";

		String prog20 = "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>"
				+ "PREFIX rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#>"
				+ "PREFIX sn: <http://www.semanticweb.org/social#>" + "SELECT ?Ind WHERE "
				+ "{ ?Ind rdf:type sn:User . FILTER (?age > 25) }";

		// EXTRA

		String prog21a = "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>"
				+ "PREFIX rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#>"
				+ "PREFIX sn: <http://www.semanticweb.org/social#>"
				+ "SELECT ?user2 ?age WHERE {?user2 sn:age ?age2 . " + "FILTER(?age2 > ?age )} ";

		
		String prog21 = "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>"
				+ "PREFIX rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#>"
				+ "PREFIX sn: <http://www.semanticweb.org/social#>"
				+ "SELECT ?user ?user2 WHERE { ?user sn:age ?age  {SELECT ?user2 ?age WHERE {?user2 sn:age ?age2 . "
				+ "FILTER(?age2 > ?age )} }  }";

		//MIRAR FUNCIONES
		String prog22 = "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>"
				+ "PREFIX rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#>"
				+ "PREFIX sn: <http://www.semanticweb.org/social#>"
				+ "SELECT (concat(?name,'hola') as ?nombre) WHERE {?user rdf:type sn:User . ?user sn:name ?name } ";

		String prog23 = "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>"
				+ "PREFIX rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#>"
				+ "PREFIX sn: <http://www.semanticweb.org/social#>"
				+ "SELECT ?Ind WHERE  {"
				+ "?Ind rdf:type sn:User .   ?Ind sn:age ?Age . FILTER(?Age > 30 || ?Age < 80)     } ";

		
		String prog24 = "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>"
				+ "PREFIX rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#>"
				+ "PREFIX sn: <http://www.semanticweb.org/social#>"
				+ "SELECT ?Ind ?Ind2 WHERE { " + " { ?Ind rdf:type sn:Event }  " + "UNION"
				+ "{SELECT ?Ind2 WHERE {?Ind2 rdf:type sn:Event } } }";
        
		String prog25 = "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n"
				+ "PREFIX owl: <http://www.w3.org/2002/07/owl#>\n" + "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>\n"
				+ "PREFIX rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n"
				+ "PREFIX sn: <http://www.semanticweb.org/social#>\n"
				+ "SELECT ?USER WHERE {\n" + "?USER sn:age ?AGE .\n" + "FILTER (?AGE > 25) .\n"
				+ "FILTER NOT EXISTS {SELECT ?AGE WHERE {\n" + "?USER2 sn:age ?AGE2 .\n" + "FILTER (?AGE < ?AGE2 ) }\n"
				+ "}}\n";

		String prog26 = "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n"
				+ "PREFIX owl: <http://www.w3.org/2002/07/owl#>\n" + "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>\n"
				+ "PREFIX rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n"
				+ "PREFIX sn: <http://www.semanticweb.org/social#>\n"
				+ "SELECT ?NUSER1 ?NUSER2 WHERE {\n" + "?USER1 sn:name ?NUSER1 . ?USER2 sn:name ?NUSER2 . \n"
				+ "?USER1 sn:age ?AU1 . ?USER2 sn:age ?AU2 . \n " + "FILTER(?AU1 > 40 && ?AU2 > 50) }\n";

		
		String prog27 = "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>"
				+ "PREFIX rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#>"
				+ "PREFIX sn: <http://www.semanticweb.org/social#>"
				+ "SELECT ?user ?user2 ?age2 WHERE {"
				+ "?user sn:age ?age2 . {SELECT ?user2 ?age2  WHERE {?user2 sn:age ?age2" + "}} . FILTER(?age2=51)} ";

		 
		String prog28 = "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n"
				+ "PREFIX owl: <http://www.w3.org/2002/07/owl#>\n" + "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>\n"
				+ "PREFIX rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n"
				+ "PREFIX sn: <http://www.semanticweb.org/social#>\n"
				+ "SELECT ?USER WHERE {\n" + "?USER sn:age ?AGE .\n" + "FILTER (?AGE > 50) .\n"
				+ "FILTER EXISTS {SELECT ?USER ?AGE  WHERE {\n" + "?USER2 sn:age ?AGE2 . \n"
				+ " FILTER (?AGE2 < ?AGE) }\n" + "}}\n";

		String prog29 = "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\r\n"
				+ "PREFIX rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\r\n"
				+ "PREFIX sn: <http://www.semanticweb.org/social#>\r\n"
				+ "SELECT ?Ind ?age ?event WHERE\r\n " + "{?Ind sn:age ?age .\r\n"
				+ "OPTIONAL { ?Ind sn:attends_to ?event }" + "MINUS {?Ind sn:age ?age . FILTER(?age < 50)}  } ";

		 
		String prog30 = "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n"
				+ "PREFIX owl: <http://www.w3.org/2002/07/owl#>\n" + "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>\n"
				+ "PREFIX rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n"
				+ "PREFIX sn: <http://www.semanticweb.org/social#>\n"
				+ "SELECT ?user (count(*) AS ?areg)  \r\n" + "WHERE {\r\n" + "  ?conf sn:added_by ?user .\r\n"
				+ " \r\n" + "}\r\n" + "GROUP BY ?user \r\n";

		//MIRAR
		String prog31 = "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n"
				+ "PREFIX owl: <http://www.w3.org/2002/07/owl#>\n" + "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>\n"
				+ "PREFIX rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n"
				+ "PREFIX sn: <http://www.semanticweb.org/social#>\n"
				+ "SELECT ?user (sum(?reg) AS ?areg) (min(?reg) as ?mreg)  \r\n" + "WHERE {\r\n"
				+ "  ?conf sn:added_by ?user .\r\n"  + "}\r\n"
				+ "GROUP BY ?user \r\n";

		//MIRAR
		String prog32 = "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n"
				+ "PREFIX owl: <http://www.w3.org/2002/07/owl#>\n" + "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>\n"
				+ "PREFIX rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n"
				+ "PREFIX sn: <http://www.semanticweb.org/social#>\n"
				+ "SELECT ?user (sum(?reg) AS ?areg) (min(?reg) as ?mreg)  \r\n" + "WHERE {\r\n"
				+ "  ?conf sn:added_by ?user .\r\n" + "   \r\n" + "}\r\n"
				+ "GROUP BY ?user \r\n" + "HAVING (SUM(?reg) > 2000)";

		//MIRAR
		String prog33 = "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n"
				+ "PREFIX owl: <http://www.w3.org/2002/07/owl#>\n" + "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>\n"
				+ "PREFIX rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n"
				+ "PREFIX sn: <http://www.semanticweb.org/social#>\n"
				+ "SELECT ?user (sum(?reg) AS ?areg) (min(?reg) as ?mreg)  \r\n" + "WHERE {\r\n"
				+ "  ?conf sn:added_by ?user .\r\n" + " { SELECT ?conf ?reg WHERE { ?conf sn:registration ?reg }} .\r\n"
				+ "}\r\n" + "GROUP BY ?user \r\n" + "HAVING (min(?reg) > 400)";

		String db1 = "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\r\n"
				+ "PREFIX owl: <http://www.w3.org/2002/07/owl#>\r\n"
				+ "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\r\n"
				+ "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>\r\n"
				+ "PREFIX fd:<http://www.semanticweb.org/food#>\r\n" + "SELECT ?u\r\n" + "WHERE {\r\n"
				+ "?u rdf:type fd:food .\r\n" + "?u fd:made_from fd:flour \r\n" + "}";

		String db2 = "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\r\n"
				+ "PREFIX owl: <http://www.w3.org/2002/07/owl#>\r\n"
				+ "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\r\n"
				+ "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>\r\n"
				+ "PREFIX fd:<http://www.semanticweb.org/food#>\r\n" + "SELECT ?u\r\n" + "WHERE {\r\n"
				+ "?u rdf:type fd:food .\r\n" + "?u fd:time ?t .\r\n" + "FILTER (?t < 30 || ?t > 60)\r\n" + "}";

		String db3 = "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\r\n"
				+ "PREFIX owl: <http://www.w3.org/2002/07/owl#>\r\n"
				+ "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\r\n"
				+ "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>\r\n"
				+ "PREFIX fd:<http://www.semanticweb.org/food#>\r\n" + "SELECT ?u\r\n" + "WHERE {\r\n"
				+ "?u rdf:type fd:food .\r\n" + "?u fd:season fd:salt .\r\n" + "{SELECT ?u (count(*) as ?l)\r\n"
				+ "WHERE { ?u fd:made_from ?m . ?u fd:time ?t . FILTER(?t<60) . FILTER(?t>0) }\r\n" + "GROUP BY ?u}\r\n"
				+ "FILTER(?l > 3) .\r\n" + "}";

		//MIRAR
		String db4 = "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\r\n"
				+ "PREFIX owl: <http://www.w3.org/2002/07/owl#>\r\n"
				+ "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\r\n"
				+ "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>\r\n"
				+ "PREFIX fd:<http://www.semanticweb.org/food#>\r\n" + "SELECT ?m\r\n" + "WHERE\r\n" + "{\r\n"
				+ "?m rdf:type fd:menu .\r\n" + "?m fd:price ?p\r\n" + "FILTER(?p>=100) ." + "FILTER EXISTS\r\n"
				+ "{\r\n" + "SELECT ?m ?d ?t ?ni WHERE { ?m fd:dish ?d . ?d fd:time ?t .\r\n" + "FILTER(?t <60) .\r\n"
				+ "{SELECT ?d (count(*) as ?ni) WHERE { ?d fd:made_from ?i}\r\n" + "GROUP BY ?d\r\n" + "}\r\n"
				+ "FILTER(?ni <= 2)\r\n" + "}" + "}" + "}";

		String db5 = "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\r\n"
				+ "PREFIX owl: <http://www.w3.org/2002/07/owl#>\r\n"
				+ "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\r\n"
				+ "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>\r\n"
				+ "PREFIX fd:<http://www.semanticweb.org/food#>\r\n" + "SELECT ?c (sum(?t) as ?tt)" + "WHERE {\r\n"
				+ "?u rdf:type fd:food .\r\n" + "?u fd:cooked ?c .\r\n" + "?u fd:time ?t .\r\n" + "}\r\n"
				+ "GROUP BY ?c";

		String db6 = "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\r\n"
				+ "PREFIX owl: <http://www.w3.org/2002/07/owl#>\r\n"
				+ "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\r\n"
				+ "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>\r\n"
				+ "PREFIX fd:<http://www.semanticweb.org/food#>\r\n" + "SELECT ?u " + "WHERE {\r\n"
				+ "?u rdf:type fd:food .\r\n" + "?u fd:cooked fd:raw .\r\n" + "?u fd:time ?t .\r\n"
				+ "OPTIONAL {?u fd:made_from fd:milk}\r\n" + "FILTER(?t >= 60)\r\n" + "}";

		String db7 = "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\r\n"
				+ "PREFIX owl: <http://www.w3.org/2002/07/owl#>\r\n"
				+ "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\r\n"
				+ "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>\r\n"
				+ "PREFIX fd:<http://www.semanticweb.org/food#>\r\n" + "SELECT ?u " + "WHERE {\r\n"
				+ "?u rdf:type fd:food .\r\n" + "?u fd:cooked fd:raw .\r\n" + "?u fd:time ?t .\r\n"
				+ "MINUS {?u fd:made_from fd:milk}\r\n" + "FILTER(?t >= 60)\r\n" + "}";

		 
		String db8 = "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\r\n"
				+ "PREFIX owl: <http://www.w3.org/2002/07/owl#>\r\n"
				+ "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\r\n"
				+ "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>\r\n"
				+ "PREFIX fd:<http://www.semanticweb.org/food#>\r\n" + "SELECT ?m\r\n" + "WHERE\r\n" + "{\r\n"
				+ "?m rdf:type fd:menu .\r\n" + "?m fd:price ?p\r\n" + "FILTER(?p>=10) ." + "FILTER NOT EXISTS\r\n"
				+ "{\r\n" + "SELECT ?m ?d ?t ?ni WHERE { ?m fd:dish ?d . ?d fd:time ?t .\r\n" + "FILTER(?t >60) .\r\n"
				+ "{SELECT ?d (count(*) as ?ni) WHERE { ?d fd:made_from ?i}\r\n" + "GROUP BY ?d\r\n" + "}\r\n"
				+ "FILTER(?ni <= 2)\r\n" + "}" + "}" + "}";

		String db9 = "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\r\n"
				+ "PREFIX owl: <http://www.w3.org/2002/07/owl#>\r\n"
				+ "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\r\n"
				+ "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>\r\n"
				+ "PREFIX fd:<http://www.semanticweb.org/food#>\r\n" + "SELECT ?m WHERE {\r\n"
				+ "{?m fd:cooked fd:raw }\r\n" + "UNION {\r\n" + "{?m fd:cooked fd:roast }\r\n" + "UNION\r\n"
				+ "{?m fd:cooked fd:bake } } }";

		 

		String filename = "C:/Users/Administrator/git/SPARQL2PL/sparql2pl/food.owl";

		 
		pSPARQL ps = new pSPARQL();

		List<List<String>> rules = ps.SPARQLtoProlog(db4, 0);
		System.out.println(rules);

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

		String t21c = " working_directory(_,\"C:/\")";
		org.jpl7.Query q21c = new org.jpl7.Query(t21c);
		System.out.print((q21c.hasSolution() ? "" : ""));
		q21c.close();
		
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
        

		String t2 = "rdf_load('" + filename + "')";
		org.jpl7.Query q2 = new org.jpl7.Query(t2);
		System.out.print((q2.hasSolution() ? "" : ""));
		q2.close();

		String t22 = "rdf(X,Y,Z)";
		org.jpl7.Query q22 = new org.jpl7.Query(t22);
		String rdfs = "";
		Map<String, Term>[] srdfs = q22.allSolutions();
		q22.close();

		for (Map<String, Term> solution : srdfs) {
			rdfs = rdfs + "rdf(" + solution.get("X") + ',' + solution.get("Y") + ',' + solution.get("Z") + ").\n";
		}

		String prule2 = "";
		System.out.println("Number of rules: " + rules.size());
		for (List<String> r : rules) {

			String dr = r.get(0);
			org.jpl7.Query drq = new org.jpl7.Query("retractall(" + dr + ")");
			System.out.print((drq.hasSolution() ? "" : ""));
			drq.close();

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

		org.jpl7.Query q3 = new org.jpl7.Query(rules.get(0).get(0));
		Map<String, Term>[] sols = q3.allSolutions();
		q3.close();

		for (Map<String, Term> solution : sols) {
			System.out.println(solution);
		}
		

		 
	};

};
