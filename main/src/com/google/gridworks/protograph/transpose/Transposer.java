package com.google.gridworks.protograph.transpose;

import java.util.LinkedList;
import java.util.List;

import com.google.gridworks.browsing.FilteredRows;
import com.google.gridworks.browsing.RowVisitor;
import com.google.gridworks.expr.ExpressionUtils;
import com.google.gridworks.model.Cell;
import com.google.gridworks.model.Column;
import com.google.gridworks.model.Project;
import com.google.gridworks.model.Row;
import com.google.gridworks.model.Recon.Judgment;
import com.google.gridworks.protograph.AnonymousNode;
import com.google.gridworks.protograph.CellNode;
import com.google.gridworks.protograph.CellTopicNode;
import com.google.gridworks.protograph.FreebaseTopicNode;
import com.google.gridworks.protograph.Link;
import com.google.gridworks.protograph.Node;
import com.google.gridworks.protograph.NodeWithLinks;
import com.google.gridworks.protograph.Protograph;
import com.google.gridworks.protograph.ValueNode;

public class Transposer {
    static public void transpose(
        Project                 project,
        FilteredRows            filteredRows,
        Protograph              protograph,
        Node                    rootNode,
        TransposedNodeFactory   nodeFactory
    ) {
        transpose(project, filteredRows, protograph, rootNode, nodeFactory, 20);
    }
    
    static public void transpose(
        Project                 project,
        FilteredRows            filteredRows,
        Protograph              protograph,
        Node                    rootNode,
        TransposedNodeFactory   nodeFactory,
        int                     limit
    ) {
        Context rootContext = new Context(rootNode, null, null, limit);
        
        filteredRows.accept(project, new RowVisitor() {
            Context                 rootContext;
            Protograph              protograph;
            Node                    rootNode;
            TransposedNodeFactory   nodeFactory;
            
            @Override
            public boolean visit(Project project, int rowIndex, Row row) {
                if (rootContext.limit <= 0 || rootContext.count < rootContext.limit) {
                    descend(project, protograph, nodeFactory, rowIndex, row, rootNode, rootContext);
                }
                
                if (rootContext.limit > 0 && rootContext.count > rootContext.limit) {
                    return true;
                }
                return false;
            }
            
            @Override
            public void start(Project project) {
                // TODO Auto-generated method stub
                
            }
            
            @Override
            public void end(Project project) {
                // TODO Auto-generated method stub
                
            }
            
            public RowVisitor init(
                Context                 rootContext,
                Protograph              protograph,
                Node                    rootNode,
                TransposedNodeFactory   nodeFactory
            ) {
                this.rootContext = rootContext;
                this.protograph = protograph;
                this.rootNode = rootNode;
                this.nodeFactory = nodeFactory;
                
                return this;
            }
        }.init(rootContext, protograph, rootNode, nodeFactory));
    }
    
    static protected void descend(
        Project project,
        Protograph protograph, 
        TransposedNodeFactory nodeFactory,
        int rowIndex, 
        Row row,
        Node node,
        Context context
    ) {
        TransposedNode tnode = null;
        
        TransposedNode parentNode = context.parent == null ? null : context.parent.transposedNode;
        Link link = context.parent == null ? null : context.link;
        
        if (node instanceof CellNode) {
            CellNode node2 = (CellNode) node;
            Column column = project.columnModel.getColumnByName(node2.columnName);
            if (column != null) {
                Cell cell = row.getCell(column.getCellIndex());
                if (cell != null && ExpressionUtils.isNonBlankData(cell.value)) {
                    if (node2 instanceof CellTopicNode &&
                        (cell.recon == null || cell.recon.judgment == Judgment.None)) {
                            return;
                    }
                    
                    context.count++;
                    if (context.limit > 0 && context.count > context.limit) {
                        return;
                    }
                    
                    tnode = nodeFactory.transposeCellNode(
                        parentNode,
                        link,
                        node2, 
                        rowIndex,
                        cell
                    );
                }
            }
        } else {
            if (node instanceof AnonymousNode) {
                tnode = nodeFactory.transposeAnonymousNode(
                    parentNode,
                    link,
                    (AnonymousNode) node,
                    rowIndex
                );
            } else if (node instanceof FreebaseTopicNode) {
                tnode = nodeFactory.transposeTopicNode(
                    parentNode,
                    link,
                    (FreebaseTopicNode) node,
                    rowIndex
                );
            } else if (node instanceof ValueNode) {
                tnode = nodeFactory.transposeValueNode(
                    parentNode,
                    link,
                    (ValueNode) node,
                    rowIndex
                );
            }
        }
        
        if (tnode != null) {
            context.transposedNode = tnode;
            context.nullifySubContextNodes();
        } /*
             else, previous rows might have set the context transposed node already,
             and we simply inherit that transposed node.
        */
        
        if (node instanceof NodeWithLinks && context.transposedNode != null) {
            NodeWithLinks node2 = (NodeWithLinks) node;
            
            int linkCount = node2.getLinkCount();
            
            for (int i = 0; i < linkCount; i++) {
                descend(
                    project, 
                    protograph, 
                    nodeFactory,
                    rowIndex,
                    row, 
                    node2.getLink(i).getTarget(), 
                    context.subContexts.get(i)
                );
            }
        }
    }
    
    static class Context {
        TransposedNode    transposedNode;
        List<Context>     subContexts;
        Context           parent;
        Link              link;
        int               count;
        int               limit;
        
        Context(Node node, Context parent, Link link, int limit) {
            this.parent = parent;
            this.link = link;
            this.limit = limit;
            
            if (node instanceof NodeWithLinks) {
                NodeWithLinks node2 = (NodeWithLinks) node;
                
                int subContextCount = node2.getLinkCount();
                
                subContexts = new LinkedList<Context>();
                for (int i = 0; i < subContextCount; i++) {
                    Link link2 = node2.getLink(i);
                    subContexts.add(
                        new Context(link2.getTarget(), this, link2, -1));
                }
            }
        }
        
        public void nullifySubContextNodes() {
            if (subContexts != null) {
                for (Context context : subContexts) {
                    context.transposedNode = null;
                    context.nullifySubContextNodes();
                }
            }
        }
    }
}
