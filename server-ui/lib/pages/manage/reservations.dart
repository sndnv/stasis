import 'package:flutter/material.dart';
import 'package:server_ui/api/api_client.dart';
import 'package:server_ui/model/reservations/crate_storage_reservation.dart';
import 'package:server_ui/pages/default/components.dart';
import 'package:server_ui/pages/manage/components/entity_table.dart';
import 'package:server_ui/pages/manage/components/extensions.dart';
import 'package:server_ui/pages/manage/components/rendering.dart';
import 'package:server_ui/pages/page_destinations.dart';

class CrateStorageReservations extends StatefulWidget {
  const CrateStorageReservations({super.key, required this.client});

  final ReservationsApiClient client;

  @override
  State createState() {
    return _CrateStorageReservationsState();
  }
}

class _CrateStorageReservationsState extends State<CrateStorageReservations> {
  @override
  Widget build(BuildContext context) {
    final queryFilter = Uri.base.queryParameters['filter']?.trim() ?? '';

    return buildPage<List<CrateStorageReservation>>(
      of: () => widget.client.getCrateStorageReservations(),
      builder: (context, reservations) {
        return EntityTable<CrateStorageReservation>(
          entities: reservations,
          actions: const [],
          appliedFilter: queryFilter,
          filterBy: (entity, filter) {
            final reservation = entity as CrateStorageReservation;
            return reservation.id.contains(filter) ||
                reservation.crate.contains(filter) ||
                reservation.origin.contains(filter) ||
                reservation.target.contains(filter);
          },
          header: const Text('Crate Storage Reservations'),
          columns: [
            EntityTableColumn(label: 'ID', sortBy: (e) => (e.id as String).toMinimizedString()),
            EntityTableColumn(label: 'Crate', sortBy: (e) => (e.crate as String).toMinimizedString()),
            EntityTableColumn(label: 'Size', sortBy: (e) => e.size),
            EntityTableColumn(label: 'Copies', sortBy: (e) => e.copies),
            EntityTableColumn(label: 'Origin', sortBy: (e) => (e.origin as String).toMinimizedString()),
            EntityTableColumn(label: 'Target', sortBy: (e) => (e.target as String).toMinimizedString()),
          ],
          entityToRow: (entity) {
            final reservation = entity as CrateStorageReservation;

            return [
              DataCell(reservation.id.asShortId()),
              DataCell(reservation.crate.asShortId()),
              DataCell(Text(reservation.size.renderFileSize())),
              DataCell(Text(reservation.copies.toString())),
              DataCell(reservation.origin.asShortId(
                link: Link(
                  buildContext: context,
                  destination: PageRouterDestination.nodes,
                  withFilter: reservation.origin,
                ),
              )),
              DataCell(reservation.target.asShortId(
                link: Link(
                  buildContext: context,
                  destination: PageRouterDestination.nodes,
                  withFilter: reservation.target,
                ),
              )),
            ];
          },
        );
      },
    );
  }
}
