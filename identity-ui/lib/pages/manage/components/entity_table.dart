import 'dart:math';

import 'package:flutter/material.dart';

class EntityTable<T> extends StatefulWidget {
  const EntityTable({
    super.key,
    required this.entities,
    required this.columns,
    required this.entityToRow,
    this.header,
    this.actions,
  });

  final List<T> entities;
  final List<DataColumn> columns;
  final List<DataCell> Function(dynamic entity) entityToRow;

  final Widget? header;
  final List<Widget>? actions;

  @override
  State createState() {
    return _EntityTableState();
  }
}

class _EntityTableState extends State<EntityTable> {
  @override
  Widget build(BuildContext context) {
    return PaginatedDataTable(
      header: widget.header,
      rowsPerPage: max(1, min(PaginatedDataTable.defaultRowsPerPage, widget.entities.length)),
      columns: widget.columns,
      actions: widget.actions,
      source: _DataSource(rows: widget.entities, entityToRow: widget.entityToRow),
      showFirstLastButtons: true,
    );
  }
}

class _DataSource<T> extends DataTableSource {
  _DataSource({required this.rows, required this.entityToRow});

  List<T> rows;
  List<DataCell> Function(T entity) entityToRow;

  @override
  DataRow? getRow(int index) {
    if (index < 0 || index >= rows.length) {
      return null;
    } else {
      final row = rows[index];

      return DataRow.byIndex(
        index: index,
        cells: entityToRow(row),
      );
    }
  }

  @override
  int get rowCount => rows.length;

  @override
  bool get isRowCountApproximate => false;

  @override
  int get selectedRowCount => 0;
}
