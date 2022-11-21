import 'package:data_table_2/data_table_2.dart';
import 'package:flutter/material.dart';
import 'package:server_ui/utils/debouncer.dart';

class EntityTable<T> extends StatefulWidget {
  const EntityTable({
    super.key,
    required this.entities,
    required this.columns,
    required this.entityToRow,
    this.header,
    this.actions,
    this.defaultSortColumn = 0,
    this.appliedFilter = '',
    this.filterBy,
  });

  final List<T> entities;
  final List<EntityTableColumn> columns;
  final List<DataCell> Function(dynamic entity) entityToRow;

  final Widget? header;
  final List<Widget>? actions;
  final int defaultSortColumn;
  final String appliedFilter;
  final bool Function(dynamic entity, String filter)? filterBy;

  @override
  State createState() {
    return _EntityTableState<T>();
  }
}

class _EntityTableState<T> extends State<EntityTable> {
  int? _sortColumnIndex;
  bool _sortAscending = false;
  late final _filterController = TextEditingController(text: widget.appliedFilter);
  final _filterDebouncer = Debouncer(timeout: const Duration(milliseconds: 500));

  @override
  Widget build(BuildContext context) {
    final columns = widget.columns
        .map((column) => DataColumn(
              label: column.label == '' && widget.filterBy != null
                  ? TextField(
                      controller: _filterController,
                      onChanged: (_) => _filterDebouncer.run(() => setState(() {})),
                      decoration: InputDecoration(
                        labelText: 'Search',
                        border: InputBorder.none,
                        suffixIcon: IconButton(
                          onPressed: () {
                            if (_filterController.text.isNotEmpty) {
                              _filterController.clear();
                              setState(() {});
                            }
                          },
                          icon: const Icon(Icons.clear),
                          splashRadius: 16.0,
                          iconSize: 16.0,
                          tooltip: 'Clear',
                        ),
                      ),
                    )
                  : Text(column.label),
              onSort: column.sortBy != null
                  ? (int columnIndex, bool ascending) => setState(() {
                        _sortColumnIndex = columnIndex;
                        _sortAscending = ascending;
                      })
                  : null,
            ))
        .toList();

    final entities = (_filterController.text.isNotEmpty
        ? widget.entities.where((e) => widget.filterBy?.call(e, _filterController.text) ?? true).toList()
        : widget.entities)
      ..sort((a, b) {
        final sortBy = widget.columns[_sortColumnIndex ?? widget.defaultSortColumn].sortBy;
        if (sortBy != null) {
          final result = sortBy.call(a).compareTo(sortBy.call(b));
          // final result = widget.columns[_sortColumnIndex].sortWith?.call(a, b) ?? 0;
          return _sortAscending ? result : result * -1;
        } else {
          return 0;
        }
      });

    final table = PaginatedDataTable2(
      header: widget.header,
      autoRowsToHeight: true,
      columns: columns,
      actions: widget.actions,
      source: _DataSource(rows: entities, entityToRow: widget.entityToRow),
      showFirstLastButtons: true,
      sortColumnIndex: _sortColumnIndex,
      sortAscending: _sortAscending,
      empty: Center(child: Container(padding: const EdgeInsets.all(20), child: const Text('No Data'))),
    );

    return table;
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

class EntityTableColumn {
  EntityTableColumn({
    required this.label,
    this.sortBy,
  });

  String label;
  dynamic Function(dynamic entity)? sortBy;
}
